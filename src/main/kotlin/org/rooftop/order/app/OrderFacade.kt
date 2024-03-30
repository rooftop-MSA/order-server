package org.rooftop.order.app

import org.rooftop.api.order.OrderReq
import org.rooftop.netx.api.Orchestrator
import org.rooftop.order.domain.Order
import org.rooftop.order.domain.data.OrderDto
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class OrderFacade(
    private val orderOrchestrator: Orchestrator<OrderDto, Order>,
) {

    fun order(token: String, orderReq: OrderReq): Mono<Order> {
        return orderOrchestrator.saga(
            1000 * 60,
            OrderDto(orderReq.userId, orderReq.productId, orderReq.quantity),
            mutableMapOf("token" to token)
        ).map {
            result -> result.decodeResultOrThrow(Order::class)
        }
    }
}
