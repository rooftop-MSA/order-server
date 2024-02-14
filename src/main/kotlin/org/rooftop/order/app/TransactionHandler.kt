package org.rooftop.order.app

import org.rooftop.netx.api.TransactionRollbackEvent
import org.rooftop.netx.api.TransactionRollbackHandler
import org.rooftop.netx.meta.TransactionHandler
import org.rooftop.order.domain.OrderRollbackEvent
import org.springframework.context.ApplicationEventPublisher
import reactor.core.publisher.Mono

@TransactionHandler
class TransactionHandler(
    private val applicationEventPublisher: ApplicationEventPublisher,
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
                "undoOrder" -> applicationEventPublisher.publishEvent(
                    OrderRollbackEvent(
                        it["orderId"]?.toLong()
                            ?: throw IllegalStateException("Transaction replay type \"undoOrder\" must have \"orderId\" field")
                    )
                )
            }
        }.map { }
    }
}
