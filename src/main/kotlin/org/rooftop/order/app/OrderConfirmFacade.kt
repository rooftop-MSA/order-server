package org.rooftop.order.app

import org.rooftop.api.order.OrderConfirmReq
import org.rooftop.api.shop.productConsumeReq
import org.rooftop.order.domain.Order
import org.rooftop.order.domain.OrderService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@Service
class OrderConfirmFacade(
    private val orderService: OrderService,
    private val transactionManager: TransactionManager<Order>,
    @Qualifier("shopWebClient") private val shopWebClient: WebClient,
) {

    fun confirmOrder(orderConfirmReq: OrderConfirmReq): Mono<Unit> {
        transactionManager.exists(orderConfirmReq.transactionId)
            .flatMap { orderService.confirmOrder(orderConfirmReq) }
            .
    }

    private fun Mono<Order>.consumeProduct(transactionId: String) {
        this.map {
            productConsumeReq {
                this.transactionId = transactionId
                this.productId = it.orderProduct.productId
                this.consumeQuantity = it.orderProduct.productQuantity
            }
        }
            .flatMap {
            shopWebClient.post()
                .uri("/v1/products/consumes")
                .bodyValue()
        }
    }
}
