package org.rooftop.order.app.event

data class PayConfirmEvent(
    val payId: Long,
    val userId: Long,
    val orderId: Long,
    val confirmState: String,
    val totalPrice: Long,
)
