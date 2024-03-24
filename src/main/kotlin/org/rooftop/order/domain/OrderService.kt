package org.rooftop.order.domain

import org.rooftop.order.domain.data.OrderDto
import org.rooftop.order.domain.data.ProductDto
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
    fun order(orderDto: OrderDto, productDto: ProductDto): Mono<Order> {
        return orderRepository.save(toOrder(orderDto, productDto))
            .switchIfEmpty(
                Mono.error {
                    throw IllegalStateException("Cannot save order")
                }
            )
    }

    private fun toOrder(orderDto: OrderDto, productDto: ProductDto): Order {
        return Order(
            id = idGenerator.generate(),
            userId = orderDto.userId,
            orderProduct = OrderProduct(
                productId = productDto.id,
                productQuantity = orderDto.quantity,
                totalPrice = orderDto.quantity * productDto.price
            ),
            state = OrderState.PENDING,
            isNew = true,
        )
    }

    @Transactional
    fun confirmOrder(orderId: Long, state: String): Mono<Order> {
        return orderRepository.findById(orderId)
            .switchIfEmpty(
                Mono.error {
                    throw IllegalArgumentException("Cannot find exists order by id \"$orderId\"")
                }
            )
            .isPending()
            .changeState(state)
            .flatMap { orderRepository.save(it) }
    }

    private fun Mono<Order>.isPending(): Mono<Order> {
        return this.map {
            when (it.state) {
                OrderState.PENDING -> it
                else -> throw IllegalArgumentException("Cannot change order state cause \"${it.id}\" order state is not PENDING")
            }
        }
    }

    private fun Mono<Order>.changeState(state: String): Mono<Order> {
        return this.map {
            when (state.lowercase()) {
                "success" -> it.success()
                "failed" -> it.fail()
                else -> throw IllegalArgumentException("Cannot find matched order state \"${state}\"")
            }
        }
    }

    @Transactional
    fun rollbackOrder(id: Long): Mono<Order> {
        return orderRepository.findById(id)
            .switchIfEmpty(
                Mono.error {
                    throw IllegalStateException("Rollback failed cause cannot find exists order by id \"$id\"")
                }
            )
            .map { it.fail() }
            .flatMap { orderRepository.save(it) }
            .switchIfEmpty(
                Mono.error {
                    throw IllegalStateException("Rollback failed cause cannot save failed order to database")
                }
            )
    }
}
