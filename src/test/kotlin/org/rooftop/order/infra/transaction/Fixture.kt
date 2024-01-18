package org.rooftop.order.infra.transaction

import org.rooftop.order.domain.OrderState
import org.rooftop.order.infra.transaction.UndoOrder

fun undoOrder(
    id: Long = 1L,
    orderState: OrderState = OrderState.FAIL,
): UndoOrder = UndoOrder(id, orderState)
