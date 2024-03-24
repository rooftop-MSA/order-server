package org.rooftop.order.domain.data

data class ProductDto(
    val id: Long,
    val sellerId: Long,
    val title: String,
    val description: String,
    val price: Long,
    val quantity: Long,
)

