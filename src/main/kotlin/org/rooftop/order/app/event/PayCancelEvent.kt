package org.rooftop.order.app.event

data class PayCancelEvent(
    val payId: Long,
    val userId: Long,
    val orderId: Long,
    val paidPrice: Long,
)
