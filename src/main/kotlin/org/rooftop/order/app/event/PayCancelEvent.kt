package org.rooftop.order.app.event

data class PayCancelEvent(
    private val payId: Long,
    private val orderId: Long,
)
