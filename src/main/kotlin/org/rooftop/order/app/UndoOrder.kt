package org.rooftop.order.app

import org.rooftop.order.domain.OrderState

data class UndoOrder(
    val id: Long,
    val orderState: OrderState,
)
