package org.rooftop.order.app

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.equals.shouldBeEqual
import org.rooftop.netx.api.SagaManager
import org.rooftop.order.Application
import org.rooftop.order.app.event.PayCancelEvent
import org.rooftop.order.domain.OrderState
import org.rooftop.order.domain.order
import org.rooftop.order.domain.repository.OrderRepository
import org.rooftop.order.server.MockIdentityServer
import org.rooftop.order.server.MockPayServer
import org.rooftop.order.server.MockShopServer
import org.springframework.boot.test.context.SpringBootTest
import kotlin.time.Duration.Companion.seconds

@SpringBootTest(
    classes = [
        Application::class,
        MockShopServer::class,
        MockPayServer::class,
        MockIdentityServer::class,
        RedisContainer::class,
    ]
)
@DisplayName("OrderRollbackHandler 클래스의")
internal class OrderRollbackHandlerTest(
    private val transactionManager: SagaManager,
    private val orderRepository: OrderRepository,
) : DescribeSpec({

    beforeEach {
        orderRepository.save(order).block()
    }

    afterEach {
        orderRepository.deleteAll().block()
    }


    describe("rollbackOrder 메소드는") {
        context("등록된 orderId를 갖고있는 PayCancelEvent 를 수신하면,") {
            val transactionId = transactionManager.startSync()

            it("Order 를 failed 상태로 변경한다.") {
                transactionManager.rollbackSync(transactionId, "for test", validPayCancelEvent)

                eventually(5.seconds) {
                    orderRepository.findById(ORDER_ID)
                        .block()!!.state shouldBeEqual OrderState.FAILED
                }
            }
        }
    }
}) {

    companion object {
        private const val ORDER_ID = 1L
        private val order = order(id = ORDER_ID)
        private val validPayCancelEvent = PayCancelEvent(
            1L,
            1L,
            ORDER_ID,
            1000L,
        )
    }
}
