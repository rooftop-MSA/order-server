package org.rooftop.order.integration

import org.rooftop.order.domain.Order
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate

internal fun R2dbcEntityTemplate.clearAll() {
    this.delete(Order::class.java)
        .all()
        .block()
}
