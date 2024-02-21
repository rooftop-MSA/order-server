package org.rooftop.order.app

import org.rooftop.netx.api.TransactionRollbackEvent
import org.rooftop.netx.api.TransactionRollbackHandler
import org.rooftop.netx.meta.TransactionHandler
import org.rooftop.order.domain.OrderService
import org.springframework.dao.OptimisticLockingFailureException
import reactor.core.publisher.Mono
import reactor.util.retry.RetrySpec
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

@TransactionHandler
class TransactionHandler(
    private val orderService: OrderService,
) {

    @TransactionRollbackHandler
    fun handleTransactionRollbackEvent(transactionRollbackEvent: TransactionRollbackEvent): Mono<Unit> {
        return Mono.just(transactionRollbackEvent)
            .map { parseReplayToMap(it.undo) }
            .dispatch()
    }

    private fun parseReplayToMap(replay: String): Map<String, String> {
        val answer = mutableMapOf<String, String>()
        replay.split(":")
            .forEach {
                val line = it.split("=")
                answer[line[0]] = line[1]
            }
        return answer
    }

    private fun Mono<Map<String, String>>.dispatch(): Mono<Unit> {
        return this.doOnNext {
            when (it["type"]) {
                "undoOrder" -> orderService.rollbackOrder(
                    it["orderId"]?.toLong()
                        ?: throw IllegalStateException("Transaction replay type \"undoOrder\" must have \"orderId\" field")
                ).retryWhen(retryOptimisticLockingFailure)
            }
        }.map { }
    }

    private companion object {
        private const val MOST_100_PERCENT_DELAY = 1.0

        private val retryOptimisticLockingFailure =
            RetrySpec.fixedDelay(Long.MAX_VALUE, 50.milliseconds.toJavaDuration())
                .jitter(MOST_100_PERCENT_DELAY)
                .filter { it is OptimisticLockingFailureException }
    }
}
