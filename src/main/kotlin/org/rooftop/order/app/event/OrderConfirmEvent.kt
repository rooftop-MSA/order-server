package org.rooftop.order.app.event

data class OrderConfirmEvent(
    val productId: Long,
    val consumeQuantity: Long,
)
