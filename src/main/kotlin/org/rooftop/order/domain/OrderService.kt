package org.rooftop.order.domain

import org.rooftop.api.order.ConfirmState
import org.rooftop.api.order.OrderConfirmReq
import org.rooftop.api.order.OrderReq
import org.rooftop.api.shop.ProductRes
import org.rooftop.order.domain.repository.OrderRepository
import org.springframework.context.event.EventListener
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import reactor.util.retry.RetrySpec
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

@Service
@Transactional(readOnly = true)
class OrderService(
    private val idGenerator: IdGenerator,
    private val orderRepository: OrderRepository,
) {

    @Transactional
    fun order(orderReq: OrderReq, product: ProductRes): Mono<Order> {
        return orderRepository.save(toOrder(orderReq, product))
            .switchIfEmpty(
                Mono.error {
                    throw IllegalStateException("Cannot save order")
                }
            )
    }

    private fun toOrder(orderReq: OrderReq, product: ProductRes): Order {
        return Order(
            id = idGenerator.generate(),
            userId = orderReq.userId,
            orderProduct = OrderProduct(
                productId = product.id,
                productQuantity = orderReq.quantity,
                totalPrice = orderReq.quantity * product.price
            ),
            state = OrderState.PENDING,
            isNew = true,
        )
    }

    @Transactional
    fun confirmOrder(orderConfirmReq: OrderConfirmReq): Mono<Order> {
        return orderRepository.findById(orderConfirmReq.orderId)
            .switchIfEmpty(
                Mono.error {
                    throw IllegalArgumentException("Cannot find exists order by id \"${orderConfirmReq.orderId}\"")
                }
            )
            .isPending()
            .changeState(orderConfirmReq)
            .flatMap { orderRepository.save(it) }
            .retryWhen(retryOptimisticLockingFailure)
    }

    private fun Mono<Order>.isPending(): Mono<Order> {
        return this.map {
            when (it.state) {
                OrderState.PENDING -> it
                else -> throw IllegalArgumentException("Cannot change order state cause \"${it.id}\" order state is not PENDING")
            }
        }
    }

    private fun Mono<Order>.changeState(orderConfirmReq: OrderConfirmReq): Mono<Order> {
        return this.map {
            when (orderConfirmReq.confirmState) {
                ConfirmState.CONFIRM_STATE_SUCCESS -> it.success()
                ConfirmState.CONFIRM_STATE_FAILED -> it.fail()
                else -> throw IllegalArgumentException("Cannot find matched order state \"${orderConfirmReq.confirmState}\"")
            }
        }
    }

    @Transactional
    @EventListener(OrderRollbackEvent::class)
    fun rollbackOrder(orderRollbackEvent: OrderRollbackEvent): Mono<Unit> {
        return orderRepository.findById(orderRollbackEvent.id)
            .switchIfEmpty(
                Mono.error {
                    throw IllegalStateException("Rollback failed cause cannot find exists order by id \"${orderRollbackEvent.id}\"")
                }
            )
            .map { it.fail() }
            .flatMap { orderRepository.save(it) }
            .switchIfEmpty(
                Mono.error {
                    throw IllegalStateException("Rollback failed cause cannot save failed order to database")
                }
            )
            .retryWhen(retryOptimisticLockingFailure)
            .map { }
    }

    private companion object {
        private const val MOST_100_PERCENT_DELAY = 1.0

        private val retryOptimisticLockingFailure =
            RetrySpec.fixedDelay(Long.MAX_VALUE, 50.milliseconds.toJavaDuration())
                .jitter(MOST_100_PERCENT_DELAY)
                .filter { it is OptimisticLockingFailureException }
    }
}
