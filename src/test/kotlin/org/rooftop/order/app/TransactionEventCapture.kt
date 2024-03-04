package org.rooftop.order.app

import io.kotest.matchers.equals.shouldBeEqual
import org.rooftop.netx.api.*
import org.rooftop.netx.idl.TransactionState
import org.rooftop.netx.meta.TransactionHandler
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicInteger

@TransactionHandler
class TransactionEventCapture {

    val events = mutableMapOf<TransactionState, AtomicInteger>()

    fun clear() {
        events.clear()
    }

    fun startShouldBeEqual(count: Int) {
        (events.get(TransactionState.TRANSACTION_STATE_START)?.get() ?: 0) shouldBeEqual count
    }

    fun joinShouldBeEqual(count: Int) {
        (events.get(TransactionState.TRANSACTION_STATE_JOIN)?.get() ?: 0) shouldBeEqual count
    }

    fun rollbackShouldBeEqual(count: Int) {
        (events.get(TransactionState.TRANSACTION_STATE_ROLLBACK)?.get() ?: 0) shouldBeEqual count
    }


    @TransactionJoinListener
    fun handleTransactionJoinEvent(transactionJoinEvent: TransactionJoinEvent): Mono<Int> {
        return Mono.fromCallable {
            events.putIfAbsent(TransactionState.TRANSACTION_STATE_JOIN, AtomicInteger(0))
            events.get(TransactionState.TRANSACTION_STATE_JOIN)!!.incrementAndGet()
        }
    }

    @TransactionRollbackListener
    fun handleRollbackEvent(transactionRollbackEvent: TransactionRollbackEvent): Mono<Int> {
        return Mono.fromCallable {
            events.putIfAbsent(TransactionState.TRANSACTION_STATE_ROLLBACK, AtomicInteger(0))
            events.get(TransactionState.TRANSACTION_STATE_ROLLBACK)!!.incrementAndGet()
        }
    }

    @TransactionStartListener
    fun handleStartEvent(transactionStartEvent: TransactionStartEvent): Mono<Int> {
        return Mono.fromCallable {
            events.putIfAbsent(TransactionState.TRANSACTION_STATE_START, AtomicInteger(0))
            events.get(TransactionState.TRANSACTION_STATE_START)!!.incrementAndGet()
        }
    }
}
