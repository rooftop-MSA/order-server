package org.rooftop.order.integration

import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.equals.shouldBeEqual
import org.rooftop.api.order.OrderRes
import org.rooftop.api.order.orderReq
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.test.web.reactive.server.WebTestClient

@AutoConfigureWebTestClient
@DisplayName("Order 통합테스트의 ")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
internal class IntegrationTest(
    private val api: WebTestClient,
    private val r2dbcEntityTemplate: R2dbcEntityTemplate,
) : DescribeSpec({

    beforeEach {
        r2dbcEntityTemplate.clearAll()
    }

    describe("주문 API는") {
        context("상품의 id, 수량, 구매자 id가 들어올 경우,") {
            it("Payment 서버에 결제를 요청하고, 사용자에게 PENDING 상태의 Order의 id를 반환한다.") {
                val result = api.order(VALID_TOKEN, orderReq)

                result.expectStatus().isOk
                    .expectBody(OrderRes::class.java)
                    .returnResult().responseBody!!.orderId::class shouldBeEqual Long::class
            }
        }
    }
}) {

    private companion object {
        private const val VALID_TOKEN = "VALID_TOKEN"
        private val orderReq = orderReq {
            this.userId = 1L
            this.productId = 1L
            this.quantity = 100
        }
    }
}
