package org.rooftop.order.app.event

data class OrderConfirmEvent(
    val payId: Long,
    val orderId: Long,
    val productId: Long,
    val consumeQuantity: Long,
)
