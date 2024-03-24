package org.rooftop.order.app

import org.rooftop.api.order.OrderReq
import org.rooftop.netx.api.OrchestratorFactory
import org.rooftop.order.domain.Order
import org.rooftop.order.domain.data.OrderDto
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class OrderFacade(
    private val orchestratorFactory: OrchestratorFactory,
) {

    fun order(token: String, orderReq: OrderReq): Mono<Order> {
        return orchestratorFactory.get<OrderDto, Order>("orderOrchestrator")
            .transaction(
                OrderDto(orderReq.userId, orderReq.productId, orderReq.quantity),
                mutableMapOf("token" to token)
            )
            .map { result ->
                if (!result.isSuccess) {
                    result.throwError()
                }
                result.decodeResult(Order::class)
            }
    }
}
