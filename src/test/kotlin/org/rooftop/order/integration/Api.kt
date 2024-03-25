package org.rooftop.order.integration

import org.rooftop.api.order.OrderReq
import org.rooftop.api.order.OrderRes
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec

fun WebTestClient.orderAndGetId(token: String, orderReq: OrderReq): Long {
    return order(token, orderReq).expectBody(OrderRes::class.java)
        .returnResult().responseBody!!.orderId
}

fun WebTestClient.order(token: String, orderReq: OrderReq): ResponseSpec {
    return this.post()
        .uri("/v1/orders")
        .header(HttpHeaders.AUTHORIZATION, token)
        .header(HttpHeaders.CONTENT_TYPE, "application/x-protobuf")
        .bodyValue(orderReq.toByteArray())
        .exchange()
}
