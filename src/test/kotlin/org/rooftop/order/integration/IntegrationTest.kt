package org.rooftop.order.integration

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldNotBe
import org.rooftop.api.identity.userGetByTokenRes
import org.rooftop.api.order.OrderRes
import org.rooftop.api.order.orderReq
import org.rooftop.api.shop.productRes
import org.rooftop.order.Application
import org.rooftop.order.app.RedisContainer
import org.rooftop.order.domain.OrderState
import org.rooftop.order.domain.repository.OrderRepository
import org.rooftop.order.server.MockIdentityServer
import org.rooftop.order.server.MockPayServer
import org.rooftop.order.server.MockShopServer
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.time.Duration.Companion.seconds

@SpringBootTest(
    classes = [
        Application::class,
        MockPayServer::class,
        MockShopServer::class,
        MockIdentityServer::class,
        RedisContainer::class,
    ],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureWebTestClient
@DisplayName("Order 통합테스트의 ")
internal class IntegrationTest(
    private val api: WebTestClient,
    private val mockPayServer: MockPayServer,
    private val mockShopServer: MockShopServer,
    private val mockIdentityServer: MockIdentityServer,
    private val r2dbcEntityTemplate: R2dbcEntityTemplate,
    private val orderRepository: OrderRepository,
) : DescribeSpec({

    afterEach {
        r2dbcEntityTemplate.clearAll()
    }

    describe("주문 API는") {
        context("상품의 id, 수량, 구매자 id가 들어올 경우,") {

            mockIdentityServer.enqueue200(userGetByTokenRes)
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

            mockIdentityServer.enqueue200(userGetByTokenRes)
            mockShopServer.enqueue400()

            it("400 Bad Request를 응답한다.") {
                val result = api.order(VALID_TOKEN, orderReq)

                result.expectStatus().isBadRequest
            }
        }

        context("Payment서버의 에러로 Order등록을 실패할경우,") {
            mockIdentityServer.enqueue200(userGetByTokenRes)
            mockShopServer.enqueue200(productRes)
            mockPayServer.enqueue500()
            mockPayServer.enqueue200()

            it("최대 5번 retry 한다.") {
                val result = api.order(VALID_TOKEN, orderReq)

                result.expectStatus().isOk
            }
        }

        context("Order서버의 잘못된 요청으로 Order등록을 실패할경우,") {
            mockIdentityServer.enqueue200(userGetByTokenRes)
            mockShopServer.enqueue200(productRes)
            mockPayServer.enqueue400()

            it("400 Bad Request를 응답하고 order를 rollback 한다.") {
                val result = api.order(VALID_TOKEN, orderReq)

                result.expectStatus().is4xxClientError
                eventually(5.seconds) {
                    val order = orderRepository.findAll().blockFirst()
                    order shouldNotBe null
                    order!!.state shouldBeEqual OrderState.FAILED
                }
            }
        }
    }
}) {

    private companion object {
        private const val USER_ID = 1L
        private const val SELLER_ID = 2L
        private const val PRODUCT_ID = 3L
        private const val VALID_TOKEN = "VALID_TOKEN"

        private val userGetByTokenRes = userGetByTokenRes {
            this.id = USER_ID
            this.name = "USER"
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
