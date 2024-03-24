package org.rooftop.order.app.event

data class PayCancelEvent(
    val payId: Long,
    val orderId: Long,
)
