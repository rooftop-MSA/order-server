package org.rooftop.order.app

import org.rooftop.netx.api.TransactionManager
import org.rooftop.netx.api.TransactionStartEvent
import org.rooftop.netx.api.TransactionStartListener
import org.rooftop.netx.meta.TransactionHandler
import org.rooftop.order.app.event.OrderConfirmEvent
import org.rooftop.order.app.event.PayCancelEvent
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

    @TransactionStartListener(event = PayConfirmEvent::class)
    fun listenPayConfirmEvent(transactionStartEvent: TransactionStartEvent): Mono<String> {
        return Mono.deferContextual {
            Mono.just(it.get<String>("transactionId") to it.get<PayConfirmEvent>("event"))
        }.flatMap { (transactionId, payConfirmEvent) ->
            orderService.confirmOrder(payConfirmEvent.orderId, payConfirmEvent.confirmState)
                .retryWhen(retryOptimisticLockingFailure)
                .map { transactionId to it }
                .onErrorResume {
                    if (it is IllegalArgumentException) {
                        return@onErrorResume Mono.empty()
                    }
                    throw it
                }
        }.filter { (_, order) ->
            order.state == OrderState.SUCCESS
        }.transformDeferredContextual { request, context ->
            request.map {
                it to context.get<PayConfirmEvent>("event")
            }
        }.flatMap { (transactionIdAndOrder, event) ->
            val transactionId = transactionIdAndOrder.first
            val order = transactionIdAndOrder.second
            transactionManager.join(
                transactionId = transactionId,
                undo = UndoOrder(order.id),
                event = OrderConfirmEvent(
                    event.payId,
                    order.id,
                    order.productId(),
                    order.productQuantity(),
                ),
            )
        }.rollbackOnError(transactionStartEvent.transactionId, transactionStartEvent)
            .contextWrite {
                it.putAllMap(
                    mapOf(
                        "transactionId" to transactionStartEvent.transactionId,
                        "event" to transactionStartEvent.decodeEvent(PayConfirmEvent::class),
                    )
                )
            }
    }

    private fun <T> Mono<T>.rollbackOnError(
        transactionId: String,
        transactionStartEvent: TransactionStartEvent
    ): Mono<T> {
        return this.doOnError {
            val payConfirmEvent = transactionStartEvent.decodeEvent(PayConfirmEvent::class)
            transactionManager.rollback(
                transactionId,
                it.message!!,
                PayCancelEvent(payConfirmEvent.payId, payConfirmEvent.orderId)
            ).subscribeOn(Schedulers.parallel())
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
