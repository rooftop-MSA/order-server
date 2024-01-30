package org.rooftop.order.app

import org.rooftop.api.order.OrderConfirmReq
import org.rooftop.api.shop.ProductConsumeReq
import org.rooftop.api.shop.productConsumeReq
import org.rooftop.order.domain.Order
import org.rooftop.order.domain.OrderService
import org.rooftop.order.domain.OrderState
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@Service
class OrderConfirmFacade(
    private val orderService: OrderService,
    private val transactionManager: TransactionManager<UndoOrder>,
    @Qualifier("shopWebClient") private val shopWebClient: WebClient,
) {

    fun confirmOrder(orderConfirmReq: OrderConfirmReq): Mono<Unit> {
        return transactionManager.exists(orderConfirmReq.transactionId)
            .joinTransaction(orderConfirmReq)
            .flatMap { orderService.confirmOrder(orderConfirmReq) }
            .commitOnSuccess(orderConfirmReq)
            .rollbackOnError(orderConfirmReq)
            .consumeProduct(orderConfirmReq.transactionId)
            .map { }
    }

    private fun Mono<String>.joinTransaction(orderConfirmReq: OrderConfirmReq): Mono<String> {
        return this.flatMap { transactionId ->
            transactionManager.join(
                transactionId,
                UndoOrder(orderConfirmReq.orderId, OrderState.PENDING)
            )
        }
    }

    private fun Mono<Order>.consumeProduct(transactionId: String): Mono<Order> {
        return this.flatMap { order ->
            shopWebClient.post()
                .uri("/v1/products/consumes")
                .header(HttpHeaders.CONTENT_TYPE, "application/x-protobuf")
                .bodyValue(toProductConsumeReq(transactionId, order).toByteArray())
                .exchangeToMono {
                    when (it.statusCode().is2xxSuccessful) {
                        true -> Mono.just(order)
                        false -> it.createError()
                    }
                }
        }
    }

    private fun toProductConsumeReq(transactionId: String, order: Order): ProductConsumeReq {
        return productConsumeReq {
            this.transactionId = transactionId
            this.productId = order.orderProduct.productId
            this.consumeQuantity = order.orderProduct.productQuantity
        }
    }

    private fun <T> Mono<T>.commitOnSuccess(orderConfirmReq: OrderConfirmReq): Mono<T> {
        return this.doOnSuccess {
            transactionManager.commit(orderConfirmReq.transactionId)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe()
        }
    }

    private fun <T> Mono<T>.rollbackOnError(orderConfirmReq: OrderConfirmReq): Mono<T> {
        return this.doOnError {
            transactionManager.rollback(orderConfirmReq.transactionId)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe()
        }
    }
}
