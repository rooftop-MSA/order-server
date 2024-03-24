package org.rooftop.order.domain.data

data class OrderDto(
    val userId: Long,
    val productId: Long,
    val quantity: Long,
)
