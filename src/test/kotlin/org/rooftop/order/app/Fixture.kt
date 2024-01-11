package org.rooftop.order.app

import org.rooftop.order.domain.OrderState

fun undoOrder(
    id: Long = 1L,
    orderState: OrderState = OrderState.FAIL,
): UndoOrder = UndoOrder(id, orderState)
