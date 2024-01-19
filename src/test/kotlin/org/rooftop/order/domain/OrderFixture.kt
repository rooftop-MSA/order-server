package org.rooftop.order.domain

fun order(
    id: Long = 1L,
    userId: Long = 2L,
    orderProduct: OrderProduct = orderProduct(),
    state: OrderState = OrderState.PENDING,
    isNew: Boolean = true,
): Order = Order(
    id = id,
    userId = userId,
    orderProduct = orderProduct,
    state = state,
    isNew = isNew
)

fun orderProduct(
    productId: Long = 2L,
    productQuantity: Long = 100,
    totalPrice: Long = 10_000_000,
): OrderProduct = OrderProduct(
    productId = productId,
    productQuantity = productQuantity,
    totalPrice = totalPrice
)
