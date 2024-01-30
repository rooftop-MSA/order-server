package org.rooftop.order.app

import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.verify
import org.rooftop.api.order.ConfirmState
import org.rooftop.api.order.orderConfirmReq
import org.rooftop.order.domain.Order
import org.rooftop.order.domain.OrderService
import org.rooftop.order.domain.OrderState
import org.rooftop.order.domain.order
import org.rooftop.order.infra.WebClientConfigurer
import org.rooftop.order.infra.transaction.undoOrder
import org.rooftop.order.server.MockShopServer
import org.springframework.test.context.ContextConfiguration
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@DisplayName("OrderConfirmFacade 클래스의")
@ContextConfiguration(
    classes = [
        OrderConfirmFacade::class,
        WebClientConfigurer::class,
        MockShopServer::class,
    ]
)
internal class OrderConfirmFacadeTest(
    private val mockShopServer: MockShopServer,
    private val orderConfirmFacade: OrderConfirmFacade,
    @MockkBean private val orderService: OrderService,
    @MockkBean private val transactionManager: TransactionManager<UndoOrder>,
) : DescribeSpec({

    beforeSpec {
        every { orderService.confirmOrder(orderConfirmReq) } returns Mono.just(successOrder)
        every { transactionManager.join(TRANSACTION_ID, any()) } returns Mono.just(TRANSACTION_ID)
        every { transactionManager.exists(TRANSACTION_ID) } returns Mono.just(TRANSACTION_ID)
        every { transactionManager.commit(TRANSACTION_ID) } returns Mono.just(Unit)
        every { transactionManager.rollback(TRANSACTION_ID) } returns Mono.just(Unit)
    }

    afterEach {
        clearAllMocks()

        every { orderService.confirmOrder(orderConfirmReq) } returns Mono.just(successOrder)
        every { transactionManager.exists(TRANSACTION_ID) } returns Mono.just(TRANSACTION_ID)
        every { transactionManager.commit(TRANSACTION_ID) } returns Mono.just(Unit)
        every { transactionManager.rollback(TRANSACTION_ID) } returns Mono.just(Unit)
    }

    describe("confirmOrder 메소드는") {
        context("존재하는 transaction 과 pending 상태의 order 를 요청을 받으면,") {
            mockShopServer.enqueue200()

            it("주문을 성공하고 transaction 을 commit 한다.") {
                val result = orderConfirmFacade.confirmOrder(orderConfirmReq)

                StepVerifier.create(result)
                    .assertNext {
                        verify(exactly = 1) { transactionManager.commit(TRANSACTION_ID) }
                        verify(exactly = 0) { transactionManager.rollback(TRANSACTION_ID) }
                    }
                    .verifyComplete()
            }
        }

        context("PENDING 상태가 아닌 order를 변경하려고 하면,") {
            mockShopServer.enqueue200()

            every { orderService.confirmOrder(orderConfirmReq) } returns Mono.error {
                throw IllegalStateException()
            }

            it("주문을 실패하고, transaction manager에 rollback을 호출한다.") {
                val result = orderConfirmFacade.confirmOrder(orderConfirmReq)

                StepVerifier.create(result)
                    .then {
                        verify(exactly = 0) { transactionManager.commit(TRANSACTION_ID) }
                        verify(exactly = 1) { transactionManager.rollback(TRANSACTION_ID) }
                    }
                    .verifyError()
            }
        }

        context("존재하지 않는 transaction id를 받으면,") {
            every { transactionManager.exists(TRANSACTION_ID) } returns Mono.error {
                throw IllegalStateException("Cannot find opened transaction id \"$TRANSACTION_ID\"")
            }

            it("주문을 실패하고, transaction manager에 rollback을 호출한다.") {
                val result = orderConfirmFacade.confirmOrder(orderConfirmReq)

                StepVerifier.create(result)
                    .then {
                        verify(exactly = 0) { transactionManager.commit(TRANSACTION_ID) }
                        verify(exactly = 1) { transactionManager.rollback(TRANSACTION_ID) }
                    }
                    .verifyError()
            }
        }
    }
}) {

    private companion object {
        private const val ORDER_ID = 1L
        private const val TRANSACTION_ID = "1"

        private val orderConfirmReq = orderConfirmReq {
            this.orderId = ORDER_ID
            this.transactionId = TRANSACTION_ID
            this.confirmState = ConfirmState.CONFIRM_STATE_SUCCESS
        }

        private val successOrder = order(id = ORDER_ID, state = OrderState.SUCCESS)
    }
}
