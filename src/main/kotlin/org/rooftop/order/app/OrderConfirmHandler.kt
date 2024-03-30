package org.rooftop.order.app

import org.rooftop.netx.api.SagaJoinEvent
import org.rooftop.netx.api.SagaJoinListener
import org.rooftop.netx.api.SuccessWith
import org.rooftop.netx.meta.SagaHandler
import org.rooftop.order.app.event.OrderConfirmEvent
import org.rooftop.order.app.event.PayCancelEvent
import org.rooftop.order.app.event.PayConfirmEvent
import org.rooftop.order.domain.OrderService
import org.rooftop.order.domain.OrderState
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.transaction.CannotCreateTransactionException
import reactor.core.publisher.Mono
import reactor.util.retry.RetrySpec
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

@SagaHandler
class OrderConfirmHandler(
    private val orderService: OrderService,
) {

    @SagaJoinListener(
        event = PayConfirmEvent::class,
        successWith = SuccessWith.PUBLISH_COMMIT,
    )
    fun listenPayConfirmEvent(sagaJoinEvent: SagaJoinEvent): Mono<OrderConfirmEvent> {
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
                event.userId,
                order.id,
                order.productId(),
                order.productQuantity(),
                order.totalPrice(),
            )
            sagaJoinEvent.setNextEvent(orderConfirmEvent)
        }.onErrorMap {
            val payConfirmEvent = sagaJoinEvent.decodeEvent(PayConfirmEvent::class)
            val payCancelEvent = PayCancelEvent(
                payConfirmEvent.payId,
                payConfirmEvent.userId,
                payConfirmEvent.orderId,
                payConfirmEvent.totalPrice,
            )
            sagaJoinEvent.setNextEvent(payCancelEvent)
            throw it
        }.contextWrite {
            it.put("event", sagaJoinEvent.decodeEvent(PayConfirmEvent::class))
        }
    }

    private companion object {
        private const val MOST_100_PERCENT_DELAY = 1.0

        private val retryOptimisticLockingFailure =
            RetrySpec.fixedDelay(Long.MAX_VALUE, 1000.milliseconds.toJavaDuration())
                .jitter(MOST_100_PERCENT_DELAY)
                .filter {
                    it is OptimisticLockingFailureException || it is CannotCreateTransactionException
                }
    }
}
