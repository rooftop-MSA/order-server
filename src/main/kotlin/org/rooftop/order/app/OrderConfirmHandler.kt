package org.rooftop.order.app

import org.rooftop.netx.api.SuccessWith
import org.rooftop.netx.api.TransactionJoinEvent
import org.rooftop.netx.api.TransactionJoinListener
import org.rooftop.netx.meta.TransactionHandler
import org.rooftop.order.app.event.OrderConfirmEvent
import org.rooftop.order.app.event.PayCancelEvent
import org.rooftop.order.app.event.PayConfirmEvent
import org.rooftop.order.domain.OrderService
import org.rooftop.order.domain.OrderState
import org.springframework.dao.OptimisticLockingFailureException
import reactor.core.publisher.Mono
import reactor.util.retry.RetrySpec
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

@TransactionHandler
class OrderConfirmHandler(
    private val orderService: OrderService,
) {

    @TransactionJoinListener(
        event = PayConfirmEvent::class,
        successWith = SuccessWith.PUBLISH_COMMIT,
    )
    fun listenPayConfirmEvent(transactionJoinEvent: TransactionJoinEvent): Mono<OrderConfirmEvent> {
        return Mono.deferContextual {
            Mono.just(it.get<PayConfirmEvent>("event"))
        }.flatMap { payConfirmEvent ->
            orderService.confirmOrder(payConfirmEvent.orderId, payConfirmEvent.confirmState)
                .retryWhen(retryOptimisticLockingFailure)
                .onErrorResume {
                    if (it is IllegalArgumentException) {
                        return@onErrorResume Mono.empty()
                    }
                    throw it
                }
        }.filter { order ->
            order.state == OrderState.SUCCESS
        }.transformDeferredContextual { request, context ->
            request.map {
                it to context.get<PayConfirmEvent>("event")
            }
        }.map { (order, event) ->
            val orderConfirmEvent = OrderConfirmEvent(
                event.payId,
                order.id,
                order.productId(),
                order.productQuantity(),
            )
            transactionJoinEvent.setNextEvent(orderConfirmEvent)
        }.onErrorMap {
            val payConfirmEvent = transactionJoinEvent.decodeEvent(PayConfirmEvent::class)
            val payCancelEvent = PayCancelEvent(payConfirmEvent.payId, payConfirmEvent.orderId)
            transactionJoinEvent.setNextEvent(payCancelEvent)
            throw it
        }.contextWrite {
            it.putAllMap(
                mapOf(
                    "transactionId" to transactionJoinEvent.transactionId,
                    "event" to transactionJoinEvent.decodeEvent(PayConfirmEvent::class),
                )
            )
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
