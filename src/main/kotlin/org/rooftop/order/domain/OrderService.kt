package org.rooftop.order.domain

import org.rooftop.api.order.OrderReq
import org.rooftop.api.shop.ProductRes
import org.rooftop.order.domain.repository.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono

@Service
@Transactional(readOnly = true)
class OrderService(
    private val idGenerator: IdGenerator,
    private val orderRepository: OrderRepository,
) {

    @Transactional
    fun order(orderReq: OrderReq, product: ProductRes): Mono<Order> {
        return createOrder(orderReq, product)
            .flatMap { orderRepository.save(it) }
            .switchIfEmpty(
                Mono.error {
                    throw IllegalStateException("Cannot save order")
                }
            )
    }

    private fun createOrder(orderReq: OrderReq, product: ProductRes): Mono<Order> {
        return Mono.fromCallable {
            createOrder(orderReq, product)
            Order(
                id = idGenerator.generate(),
                orderProduct = OrderProduct(
                    productId = product.id,
                    productQuantity = orderReq.quantity,
                    totalPrice = orderReq.quantity * product.price
                ),
                state = OrderState.PENDING,
                isNew = true,
            )
        }
    }
}
