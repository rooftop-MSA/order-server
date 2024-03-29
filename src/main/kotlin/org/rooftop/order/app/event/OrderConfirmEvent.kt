package org.rooftop.order.app.event

data class OrderConfirmEvent(
    val payId: Long,
    val userId: Long,
    val orderId: Long,
    val productId: Long,
    val consumeQuantity: Long,
    val totalPrice: Long,
)
