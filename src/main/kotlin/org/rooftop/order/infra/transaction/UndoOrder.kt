package org.rooftop.order.infra.transaction

import org.rooftop.order.domain.OrderState

data class UndoOrder(
    val id: Long,
    val orderState: OrderState,
)
