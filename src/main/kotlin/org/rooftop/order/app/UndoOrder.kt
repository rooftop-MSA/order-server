package org.rooftop.order.app

import org.rooftop.order.domain.OrderState

data class UndoOrder(
    private val id: Long,
    private val orderState: OrderState,
)
