package org.rooftop.order.integration

import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.mockk.every
import org.rooftop.api.identity.userGetByIdRes
import org.rooftop.api.order.ConfirmState
import org.rooftop.api.order.OrderRes
import org.rooftop.api.order.orderConfirmReq
import org.rooftop.api.order.orderReq
import org.rooftop.api.shop.productRes
import org.rooftop.order.Application
import org.rooftop.order.app.TransactionIdGenerator
import org.rooftop.order.domain.repository.R2dbcConfigurer
import org.rooftop.order.server.MockIdentityServer
import org.rooftop.order.server.MockPayServer
import org.rooftop.order.server.MockShopServer
import org.rooftop.shop.infra.transaction.RedisContainerConfigurer
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.reactive.server.WebTestClient

@AutoConfigureWebTestClient
@DisplayName("Order 통합테스트의 ")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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
internal class IntegrationTest(
    private val api: WebTestClient,
    private val mockPayServer: MockPayServer,
    private val mockShopServer: MockShopServer,
    private val mockIdentityServer: MockIdentityServer,
    private val r2dbcEntityTemplate: R2dbcEntityTemplate,
    @MockkBean private val transactionIdGenerator: TransactionIdGenerator,
) : DescribeSpec({

    every { transactionIdGenerator.generate() } returns "1"

    afterEach {
        r2dbcEntityTemplate.clearAll()
    }

    describe("주문 API는") {
        context("상품의 id, 수량, 구매자 id가 들어올 경우,") {

            mockIdentityServer.enqueue200(userGetByIdRes, sellerGetByIdRes)
            mockShopServer.enqueue200(productRes)
            mockPayServer.enqueue200()

            it("Payment 서버에 결제를 요청하고, 사용자에게 PENDING 상태인 Order의 id를 반환한다.") {
                val result = api.order(VALID_TOKEN, orderReq)

                result.expectStatus().isOk
                    .expectBody(OrderRes::class.java)
                    .returnResult().responseBody!!.orderId::class shouldBeEqual Long::class
            }
        }

        context("존재하지 않는 buyer의 id가 들어올 경우,") {

            mockIdentityServer.enqueue400()

            it("400 Bad Request를 응답한다.") {
                val result = api.order(VALID_TOKEN, orderReq)

                result.expectStatus().isBadRequest
            }
        }

        context("존재하지 않는 product의 id가 들어올 경우,") {

            mockIdentityServer.enqueue200(userGetByIdRes)
            mockShopServer.enqueue400()

            it("400 Bad Request를 응답한다.") {
                val result = api.order(VALID_TOKEN, orderReq)

                result.expectStatus().isBadRequest
            }
        }

        context("존재하지 않는 seller의 id가 들어올 경우,") {

            mockIdentityServer.enqueue200(userGetByIdRes)
            mockShopServer.enqueue200(productRes)
            mockIdentityServer.enqueue400()

            it("400 Bad Request를 응답한다.") {
                val result = api.order(VALID_TOKEN, orderReq)

                result.expectStatus().isBadRequest
            }
        }
    }

    describe("주문 확정 API는") {
        context("PENDING 상태인 주문을 확정하는 요청이 들어올 경우,") {

            every { transactionIdGenerator.generate() } returns "1"

            mockIdentityServer.enqueue200(userGetByIdRes, sellerGetByIdRes)
            mockShopServer.enqueue200(productRes)
            mockShopServer.enqueue200()
            mockPayServer.enqueue200()

            val orderId = api.orderAndGetId(VALID_TOKEN, orderReq)

            val orderConfirmReq = orderConfirmReq {
                this.transactionId = "1"
                this.orderId = orderId
                this.confirmState = ConfirmState.CONFIRM_STATE_SUCCESS
            }

            it("200 Ok 를 응답한다.") {
                val result = api.confirmOrder(VALID_TOKEN, orderConfirmReq)

                result.expectStatus().isOk
            }
        }
    }
}) {

    private companion object {
        private const val USER_ID = 1L
        private const val SELLER_ID = 2L
        private const val PRODUCT_ID = 3L
        private const val VALID_TOKEN = "VALID_TOKEN"

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
    }
}
