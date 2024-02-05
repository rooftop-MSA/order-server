package org.rooftop.order.app

import org.rooftop.netx.api.TransactionRollbackEvent
import org.rooftop.order.domain.OrderRollbackEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class TransactionHandler(
    private val applicationEventPublisher: ApplicationEventPublisher,
) {

    @EventListener(TransactionRollbackEvent::class)
    fun handleTransactionRollbackEvent(transactionRollbackEvent: TransactionRollbackEvent): Mono<Unit> {
        return Mono.just(transactionRollbackEvent)
            .map { parseReplayToMap(it.replay) }
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
