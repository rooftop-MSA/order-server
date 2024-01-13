package org.rooftop.shop.infra.transaction

import org.springframework.boot.test.context.TestComponent
import org.springframework.context.event.EventListener
import kotlin.reflect.KClass

@TestComponent
class EventCapture {

    private val eventCapture: MutableMap<KClass<out Any>, Long> = mutableMapOf()
    private lateinit var behavior: (Any) -> Unit

    fun clear() {
        eventCapture.clear()
    }

    fun capturedCount(type: KClass<*>): Long {
        return eventCapture[type] ?: 0
    }

    @EventListener(Any::class)
    fun captureEvent(type: Any) {
        eventCapture[type::class] = eventCapture.getOrDefault(type::class, 0) + 1
    }
}
