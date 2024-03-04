package org.rooftop.order.domain

import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.equality.shouldBeEqualUsingFields
import io.mockk.every
import org.rooftop.api.order.ConfirmState
import org.rooftop.api.order.orderConfirmReq
import org.rooftop.order.domain.repository.OrderRepository
import org.springframework.test.context.ContextConfiguration
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@DisplayName("OrderService 클래스의")
@ContextConfiguration(classes = [OrderService::class])
internal class OrderServiceTest(
    private val orderService: OrderService,
    @MockkBean private val idGenerator: IdGenerator,
    @MockkBean private val orderRepository: OrderRepository,
) : DescribeSpec({

    every { orderRepository.save(any()) } returns Mono.just(pendingOrder.success())
    every { orderRepository.findById(PENDING_ORDER_ID) } returns Mono.just(pendingOrder)
    every { orderRepository.findById(SUCCESS_ORDER_ID) } returns Mono.just(successOrder)

    describe("confirmOrder 메소드는") {
        context("PENDING 상태의 Order 를 변경하는 요청이 들어오면,") {

            it("Order 의 상태를 변경한다.") {
                val result = orderService.confirmOrder(PENDING_ORDER_ID, "success")

                StepVerifier.create(result)
                    .assertNext {
                        it shouldBeEqualUsingFields pendingOrder.success()
                    }
                    .verifyComplete()
            }
        }

        context("PENDING 이 아닌 상태의 Order 를 변경하는 요청이 들어오면,") {

            it("IllegalArgumentException 을 던진다.") {
                val result = orderService.confirmOrder(SUCCESS_ORDER_ID, "fail")

                StepVerifier.create(result)
                    .verifyErrorMessage(
                        "Cannot change order state cause \"$SUCCESS_ORDER_ID\" order state is not PENDING"
                    )
            }
        }
    }
}) {

    companion object {
        private const val PENDING_ORDER_ID = 1L
        private const val SUCCESS_ORDER_ID = 2L

        private val pendingOrder = order(
            id = PENDING_ORDER_ID,
            state = OrderState.PENDING
        )

        private val successOrder = order(
            id = SUCCESS_ORDER_ID,
            state = OrderState.SUCCESS
        )
    }

}
