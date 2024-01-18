package org.rooftop.order.infra.transaction

import org.rooftop.api.transaction.Transaction
import org.rooftop.api.transaction.TransactionState
import org.rooftop.api.transaction.transaction
import org.rooftop.order.app.TransactionPublisher
import org.rooftop.order.domain.Order
import org.rooftop.order.domain.OrderState
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Range
import org.springframework.data.redis.connection.stream.Record
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class OrderTransactionPublisher(
    private val eventPublisher: ApplicationEventPublisher,
    @Value("\${distributed.transaction.server.id}") private val transactionServerId: String,
    @Qualifier("transactionServer") private val transactionServer: ReactiveRedisTemplate<String, ByteArray>,
    @Qualifier("undoServer") private val orderUndoServer: ReactiveRedisTemplate<String, UndoOrder>,
) : TransactionPublisher<Order> {

    override fun join(transactionId: String, state: Order): Mono<String> {
        return joinOrStartTransaction()
            .undoBeforeState(UndoOrder(state.id, OrderState.PENDING))
            .doOnSuccess {
                eventPublisher.publishEvent(TransactionJoinedEvent(it))
            }
            .contextWrite { context ->
                context.put("transactionId", transactionId)
            }
    }

    private fun joinOrStartTransaction(): Mono<String> {
        return Mono.deferContextual<String> { Mono.just(it["transactionId"]) }
            .flatMap { transactionId ->
                publishTransaction(transactionId, transaction {
                    id = transactionId
                    serverId = transactionServerId
                    state = TransactionState.TRANSACTION_STATE_JOIN
                })
            }
    }

    private fun Mono<String>.undoBeforeState(state: UndoOrder): Mono<String> {
        return this.flatMap { transactionId ->
            orderUndoServer.opsForValue().set("ORDER:$transactionId", state)
                .flatMap {
                    when (it) {
                        true -> Mono.just(it)
                        false -> Mono.error {
                            IllegalStateException("error occurred cause set undo fail")
                        }
                    }
                }
                .transformTransactionId()
        }
    }

    private fun Mono<*>.transformTransactionId(): Mono<String> {
        return this.flatMap {
            Mono.deferContextual { Mono.just(it["transactionId"]) }
        }
    }

    override fun commit(transactionId: String): Mono<Unit> {
        return findOpenedTransaction(transactionId)
            .publishTransaction(transaction {
                id = transactionId
                serverId = transactionServerId
                state = TransactionState.TRANSACTION_STATE_COMMIT
            })
            .contextWrite { it.put("transactionId", transactionId) }
            .map { }
    }

    override fun rollback(transactionId: String): Mono<Unit> {
        return findOpenedTransaction(transactionId)
            .publishTransaction(transaction {
                id = transactionId
                serverId = transactionServerId
                state = TransactionState.TRANSACTION_STATE_ROLLBACK
            })
            .contextWrite { it.put("transactionId", transactionId) }
            .map { }
    }

    private fun findOpenedTransaction(transactionId: String): Mono<String> {
        return transactionServer.opsForStream<String, ByteArray>()
            .range(transactionId, Range.open("-", "+"))
            .map { Transaction.parseFrom(it.value[DATA].toString().toByteArray()) }
            .filter { it.serverId == transactionServerId }
            .next()
            .switchIfEmpty(
                Mono.error {
                    IllegalStateException("Cannot find opened transaction id \"$transactionId\"")
                }
            )
            .transformTransactionId()
    }

    private fun Mono<String>.publishTransaction(transaction: Transaction): Mono<String> {
        return this.flatMap {
            publishTransaction(it, transaction)
        }
    }

    private fun publishTransaction(transactionId: String, transaction: Transaction): Mono<String> {
        return transactionServer.opsForStream<String, ByteArray>()
            .add(
                Record.of<String?, String?, ByteArray?>(mapOf(DATA to transaction.toByteArray()))
                    .withStreamKey(transactionId)
            )
            .transformTransactionId()
    }

    companion object {
        private const val DATA = "data"
    }
}
