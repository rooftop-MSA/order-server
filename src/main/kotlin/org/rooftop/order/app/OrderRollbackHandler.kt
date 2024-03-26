package org.rooftop.order.app

import org.rooftop.netx.api.TransactionRollbackEvent
import org.rooftop.netx.api.TransactionRollbackListener
import org.rooftop.netx.meta.TransactionHandler
import org.rooftop.order.app.event.PayCancelEvent
import org.rooftop.order.domain.Order
import org.rooftop.order.domain.OrderService
import org.springframework.dao.OptimisticLockingFailureException
import reactor.core.publisher.Mono
import reactor.util.retry.RetrySpec
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

@TransactionHandler
class OrderRollbackHandler(
    private val orderService: OrderService,
) {

    @TransactionRollbackListener(event = PayCancelEvent::class)
    fun rollbackOrder(transactionRollbackEvent: TransactionRollbackEvent): Mono<Order> {
        return Mono.fromCallable {
            transactionRollbackEvent.decodeUndo(UndoOrder::class)
        }.flatMap {
            orderService.rollbackOrder(it.id)
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
                .filter { it is OptimisticLockingFailureException }
    }
}
