package org.rooftop.order.domain.repository

import org.rooftop.order.domain.Order
import org.springframework.data.r2dbc.repository.R2dbcRepository

interface OrderRepository : R2dbcRepository<Order, Long>
