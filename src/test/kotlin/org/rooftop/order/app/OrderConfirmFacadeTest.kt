package org.rooftop.order.app

import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import org.rooftop.netx.api.TransactionManager
import org.rooftop.netx.meta.EnableDistributedTransaction
import org.rooftop.order.app.event.PayConfirmEvent
import org.rooftop.order.domain.OrderService
import org.rooftop.order.domain.OrderState
import org.rooftop.order.domain.order
import org.rooftop.order.infra.WebClientConfigurer
import org.rooftop.order.server.MockShopServer
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import reactor.core.publisher.Mono
import kotlin.time.Duration.Companion.seconds

@EnableDistributedTransaction
@DisplayName("OrderConfirmHandler 클래스의")
@ContextConfiguration(
    classes = [
        OrderConfirmHandler::class,
        WebClientConfigurer::class,
        MockShopServer::class,
        RedisContainer::class,
        TransactionEventCapture::class,
    ]
)
@TestPropertySource("classpath:application.properties")
internal class OrderConfirmFacadeTest(
    private val mockShopServer: MockShopServer,
    private val transactionManager: TransactionManager,
    private val transactionEventCapture: TransactionEventCapture,
    @MockkBean private val orderService: OrderService,
) : DescribeSpec({

    mockShopServer.enqueue200()
    every { orderService.confirmOrder(any(), any()) } returns Mono.just(successOrder)

    afterEach {
        mockShopServer.enqueue200()
        transactionEventCapture.clear()
        every { orderService.confirmOrder(any(), any()) } returns Mono.just(successOrder)
    }

    describe("listenPayConfirmEvent 메소드는") {
        context("PayConfirmEvent 를 가진 TransactionStartEvent가 발행되면,") {

            it("주문을 성공하고 TransactionJoinEvent를 발행한다.") {
                transactionManager.start(UNDO, payConfirmEvent).subscribe()

                eventually(5.seconds) {
                    transactionEventCapture.joinShouldBeEqual(1)
                }
            }
        }

        context("PENDING 상태가 아닌 order를 변경하려고 하면,") {

            every {
                orderService.confirmOrder(any(), any())
            } returns Mono.error { IllegalStateException("illegal state exception") }

            it("주문을 실패하고, transaction manager에 rollback을 호출한다.") {
                transactionManager.start(UNDO, payConfirmEvent).subscribe()

                eventually(5.seconds) {
                    transactionEventCapture.joinShouldBeEqual(0)
                    transactionEventCapture.rollbackShouldBeEqual(1)
                }
            }
        }
    }
}) {

    private companion object {
        private const val UNDO = "UNDO"
        private const val ORDER_ID = 1L

        private val payConfirmEvent = PayConfirmEvent(1L, ORDER_ID, "success", 1000L)

        private val successOrder = order(id = ORDER_ID, state = OrderState.SUCCESS)
    }
}
