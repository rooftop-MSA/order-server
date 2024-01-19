package org.rooftop.order.app

import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import org.rooftop.api.identity.userGetByIdRes
import org.rooftop.api.order.orderReq
import org.rooftop.api.shop.productRes
import org.rooftop.order.Application
import org.rooftop.order.domain.Order
import org.rooftop.order.domain.order
import org.rooftop.order.domain.orderProduct
import org.rooftop.order.domain.repository.R2dbcConfigurer
import org.rooftop.order.server.MockIdentityServer
import org.rooftop.order.server.MockPayServer
import org.rooftop.order.server.MockShopServer
import org.rooftop.shop.infra.transaction.RedisContainerConfigurer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import reactor.test.StepVerifier

@SpringBootTest
@DisplayName("OrderFacade 클래스의")
@TestPropertySource("classpath:application.properties")
@ContextConfiguration(
    classes = [
        Application::class,
        MockPayServer::class,
        MockShopServer::class,
        R2dbcConfigurer::class,
        MockIdentityServer::class,
        RedisContainerConfigurer::class,
    ]
)
internal class OrderFacadeTest(
    private val orderFacade: OrderFacade,
    private val mockPayServer: MockPayServer,
    private val mockShopServer: MockShopServer,
    private val mockIdentityServer: MockIdentityServer,
) : DescribeSpec({

    describe("order 메소드는") {
        context("존재하는 상품, 판매자, 구매자 에 대한 orderReq 를 받으면,") {

            mockIdentityServer.enqueue200(userGetByIdRes, sellerGetByIdRes)
            mockPayServer.enqueue200()
            mockShopServer.enqueue200(productRes)

            it("주문을 PENDING 상태로 생성하고, 분산 트랜잭션을 시작 한다.") {
                val result = orderFacade.order(orderReq).log()

                StepVerifier.create(result)
                    .assertNext {
                        it.shouldBeEqualToIgnoringFields(
                            order,
                            Order::id,
                            Order::userId,
                            Order::modifiedAt,
                            Order::createdAt
                        )
                    }
                    .verifyComplete()
            }
        }

        context("존재하지 않은 buyer의 id가 들어오면,") {

            mockIdentityServer.enqueue400()

            it("IllegalArgumentException을 던진다.") {
                val result = orderFacade.order(orderReq).log()

                StepVerifier.create(result)
                    .verifyError(IllegalArgumentException::class.java)
            }
        }

        context("존재하지 않는 product의 id가 들어오면,") {
            mockIdentityServer.enqueue200(userGetByIdRes)
            mockShopServer.enqueue400()

            it("IllegalArgumentException을 던진다.") {
                val result = orderFacade.order(orderReq).log()

                StepVerifier.create(result)
                    .verifyError(IllegalArgumentException::class.java)
            }
        }

        context("존재하지 않는 seller의 id가 들어오면,") {
            mockIdentityServer.enqueue200(userGetByIdRes)
            mockShopServer.enqueue200(productRes)
            mockIdentityServer.enqueue400()

            it("IllegalArgumentException을 던진다.") {
                val result = orderFacade.order(orderReq).log()

                StepVerifier.create(result)
                    .verifyError(IllegalArgumentException::class.java)
            }
        }
    }
}) {

    private companion object {
        private const val USER_ID = 1L
        private const val SELLER_ID = 2L
        private const val PRODUCT_ID = 3L

        private val userGetByIdRes = userGetByIdRes {
            this.id = USER_ID
            this.name = "USER"
        }

        private val sellerGetByIdRes = userGetByIdRes {
            this.id = SELLER_ID
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
