package org.rooftop.order.app

import org.rooftop.netx.api.SagaRollbackEvent
import org.rooftop.netx.api.SagaRollbackListener
import org.rooftop.netx.meta.SagaHandler
import org.rooftop.order.app.event.PayCancelEvent
import org.rooftop.order.domain.Order
import org.rooftop.order.domain.OrderService
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.transaction.CannotCreateTransactionException
import reactor.core.publisher.Mono
import reactor.util.retry.RetrySpec
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

@SagaHandler
class OrderRollbackHandler(
    private val orderService: OrderService,
) {

    @SagaRollbackListener(event = PayCancelEvent::class)
    fun rollbackOrder(sagaRollbackEvent: SagaRollbackEvent): Mono<Order> {
        return Mono.fromCallable {
            sagaRollbackEvent.decodeEvent(PayCancelEvent::class)
        }.flatMap { payCancelEvent ->
            orderService.rollbackOrder(payCancelEvent.orderId)
                .retryWhen(retryOptimisticLockingFailure)
                .onErrorResume {
                    if (it is IllegalStateException) {
                        return@onErrorResume Mono.empty()
                    }
                    throw it
                }
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
