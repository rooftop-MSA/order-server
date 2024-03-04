package org.rooftop.order.app

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.equality.FieldsEqualityCheckConfig
import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import org.rooftop.api.identity.userGetByTokenRes
import org.rooftop.api.order.orderReq
import org.rooftop.api.shop.productRes
import org.rooftop.netx.meta.EnableDistributedTransaction
import org.rooftop.order.Application
import org.rooftop.order.domain.Order
import org.rooftop.order.domain.order
import org.rooftop.order.domain.orderProduct
import org.rooftop.order.domain.repository.R2dbcConfigurer
import org.rooftop.order.server.MockIdentityServer
import org.rooftop.order.server.MockPayServer
import org.rooftop.order.server.MockShopServer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import reactor.test.StepVerifier
import kotlin.time.Duration.Companion.seconds

@SpringBootTest
@EnableDistributedTransaction
@DisplayName("OrderFacade 클래스의")
@TestPropertySource("classpath:application.properties")
@ContextConfiguration(
    classes = [
        Application::class,
        MockShopServer::class,
        MockPayServer::class,
        R2dbcConfigurer::class,
        MockIdentityServer::class,
        RedisContainer::class,
        TransactionEventCapture::class,
    ]
)
internal class OrderFacadeTest(
    private val orderFacade: OrderFacade,
    private val mockPayServer: MockPayServer,
    private val mockShopServer: MockShopServer,
    private val mockIdentityServer: MockIdentityServer,
    private val transactionEventCapture: TransactionEventCapture,
) : DescribeSpec({

    beforeEach {
        transactionEventCapture.clear()
    }

    describe("order 메소드는") {
        context("존재하는 상품, 판매자, 구매자 에 대한 orderReq 를 받으면,") {

            mockIdentityServer.enqueue200(userGetByTokenRes)
            mockPayServer.enqueue200()
            mockShopServer.enqueue200(productRes)

            it("주문을 PENDING 상태로 생성하고, 분산 트랜잭션을 시작 한다.") {
                val result = orderFacade.order(VALID_TOKEN, orderReq)
                    .block()

                eventually(5.seconds) {
                    result.shouldBeEqualToComparingFields(
                        order,
                        FieldsEqualityCheckConfig(
                            propertiesToExclude = listOf(
                                Order::id,
                                Order::userId,
                                Order::modifiedAt,
                                Order::createdAt
                            )
                        )
                    )
                    transactionEventCapture.startShouldBeEqual(1)
                }
            }
        }

        context("존재하지 않은 buyer의 id가 들어오면,") {

            mockIdentityServer.enqueue400()

            it("IllegalArgumentException을 던진다.") {
                val result = orderFacade.order(VALID_TOKEN, orderReq).log()

                StepVerifier.create(result)
                    .verifyError(IllegalArgumentException::class.java)
                transactionEventCapture.startShouldBeEqual(0)
            }
        }

        context("존재하지 않는 product의 id가 들어오면,") {
            mockIdentityServer.enqueue200(userGetByTokenRes)
            mockShopServer.enqueue400()

            it("IllegalArgumentException을 던진다.") {
                val result = orderFacade.order(VALID_TOKEN, orderReq).log()

                StepVerifier.create(result)
                    .verifyError(IllegalArgumentException::class.java)
                transactionEventCapture.startShouldBeEqual(0)
            }
        }
    }
}) {

    private companion object {
        private const val USER_ID = 1L
        private const val SELLER_ID = 2L
        private const val PRODUCT_ID = 3L
        private const val VALID_TOKEN = "valid token"

        private val userGetByTokenRes = userGetByTokenRes {
            this.id = USER_ID
            this.name = "SELLER"
        }

        private val productRes = productRes {
            this.id = PRODUCT_ID
            this.sellerId = SELLER_ID
            this.price = 1000L
            this.quantity = 100
            this.title = "TITLE"
            this.description = "DESCRIPTION"
        }

        private val orderReq = orderReq {
            this.userId = USER_ID
            this.productId = PRODUCT_ID
            this.quantity = 100
        }

        private val order = order(
            userId = USER_ID,
            orderProduct = orderProduct(
                productId = productRes.id,
                productQuantity = productRes.quantity,
                totalPrice = productRes.quantity * productRes.price
            )
        )
    }
}
