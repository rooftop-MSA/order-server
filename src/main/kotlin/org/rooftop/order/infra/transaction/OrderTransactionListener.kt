package org.rooftop.order.infra.transaction

import org.rooftop.api.transaction.Transaction
import org.rooftop.api.transaction.TransactionState
import org.rooftop.order.app.UndoOrder
import org.rooftop.order.domain.OrderRollbackEvent
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.stream.StreamReceiver
import org.springframework.data.redis.stream.StreamReceiver.StreamReceiverOptions
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers

@Component
class OrderTransactionListener(
    private val eventPublisher: ApplicationEventPublisher,
    @Qualifier("transactionServerConnectionFactory") private val connectionFactory: ReactiveRedisConnectionFactory,
    @Qualifier("undoServer") private val orderUndoServer: ReactiveRedisTemplate<String, UndoOrder>,
) {

    @EventListener(TransactionJoinedEvent::class)
    fun subscribeStream(transactionJoinedEvent: TransactionJoinedEvent): Flux<Transaction> {
        val options = StreamReceiverOptions.builder()
            .pollTimeout(java.time.Duration.ofMillis(100))
            .build()

        val receiver = StreamReceiver.create(connectionFactory, options)

        return receiver.receive(StreamOffset.fromStart(transactionJoinedEvent.transactionId))
            .subscribeOn(Schedulers.boundedElastic())
            .map { Transaction.parseFrom(it.value["data"]?.toByteArray()) }
            .dispatch()
    }

    private fun Flux<Transaction>.dispatch(): Flux<Transaction> {
        return this.filter { it.state == TransactionState.TRANSACTION_STATE_ROLLBACK }
            .flatMap { transaction ->
                orderUndoServer.opsForValue()["ORDER:${transaction.id}"]
                    .doOnNext { eventPublisher.publishEvent(OrderRollbackEvent(it.id)) }
                    .map { transaction }
                    .flatMap {
                        orderUndoServer.opsForValue().delete("ORDER:${transaction.id}")
                            .map { transaction }
                            .retry()
                    }
            }
    }
}
