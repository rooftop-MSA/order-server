package org.rooftop.order.infra.transaction

import org.rooftop.order.domain.OrderState
import org.rooftop.order.app.UndoOrder

fun undoOrder(
    id: Long = 1L,
    orderState: OrderState = OrderState.FAILED,
): UndoOrder = UndoOrder(id, orderState)
