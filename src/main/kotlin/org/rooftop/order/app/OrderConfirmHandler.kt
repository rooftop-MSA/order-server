package org.rooftop.order.app

import org.rooftop.netx.api.TransactionManager
import org.rooftop.netx.api.TransactionStartEvent
import org.rooftop.netx.api.TransactionStartListener
import org.rooftop.netx.meta.TransactionHandler
import org.rooftop.order.app.event.OrderConfirmEvent
import org.rooftop.order.app.event.PayConfirmEvent
import org.rooftop.order.domain.OrderService
import org.rooftop.order.domain.OrderState
import org.springframework.dao.OptimisticLockingFailureException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.util.retry.RetrySpec
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

@TransactionHandler
class OrderConfirmHandler(
    private val orderService: OrderService,
    private val transactionManager: TransactionManager,
) {

    @TransactionStartListener(
        event = PayConfirmEvent::class,
        noRetryFor = [IllegalArgumentException::class]
    )
    fun listenPayConfirmEvent(transactionStartEvent: TransactionStartEvent): Mono<String> {
        return Mono.deferContextual {
            Mono.just(it.get<String>("transactionId") to it.get<PayConfirmEvent>("event"))
        }.flatMap { (transactionId, payConfirmEvent) ->
            orderService.confirmOrder(payConfirmEvent.orderId, payConfirmEvent.confirmState)
                .retryWhen(retryOptimisticLockingFailure)
                .map { transactionId to it }
        }.filter { (_, order) ->
            order.state == OrderState.SUCCESS
        }.flatMap { (transactionId, order) ->
            transactionManager.join(
                transactionId = transactionId,
                undo = UndoOrder(order.id),
                event = OrderConfirmEvent(
                    order.orderProduct.productId,
                    order.orderProduct.productQuantity,
                ),
            )
        }.rollbackOnError(transactionStartEvent.transactionId).contextWrite {
            it.putAllMap(
                mapOf(
                    "transactionId" to transactionStartEvent.transactionId,
                    "event" to transactionStartEvent.decodeEvent(PayConfirmEvent::class),
                )
            )
        }
    }

    private fun <T> Mono<T>.rollbackOnError(transactionId: String): Mono<T> {
        return this.doOnError {
            transactionManager.rollback(transactionId, it.message!!)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe()
        }
    }

    private companion object {
        private const val MOST_100_PERCENT_DELAY = 1.0

        private val retryOptimisticLockingFailure =
            RetrySpec.fixedDelay(Long.MAX_VALUE, 1000.milliseconds.toJavaDuration())
                .jitter(MOST_100_PERCENT_DELAY)
                .filter { it is OptimisticLockingFailureException }
    }
}
