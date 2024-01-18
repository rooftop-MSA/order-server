package org.rooftop.order.app

import org.rooftop.api.identity.UserGetByIdRes
import org.rooftop.api.order.OrderReq
import org.rooftop.api.pay.payRegisterOrderReq
import org.rooftop.api.shop.ProductRes
import org.rooftop.order.domain.Order
import org.rooftop.order.domain.OrderService
import org.rooftop.order.domain.OrderState
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

@Service
class OrderFacade(
    private val orderService: OrderService,
    private val transactionIdGenerator: TransactionIdGenerator,
    private val orderTransactionPublisher: TransactionPublisher<UndoOrder>,
    @Qualifier("payWebClient") private val payWebClient: WebClient,
    @Qualifier("shopWebClient") private val shopWebClient: WebClient,
    @Qualifier("identityWebClient") private val identityWebClient: WebClient,
) {

    fun order(orderReq: OrderReq): Mono<Order> {
        return startWithTransactionId()
            .existBuyer(orderReq.userId)
            .existProduct(orderReq.productId)
            .existSeller()
            .order(orderReq)
            .createAndJoinTransaction()
            .registerOrderToPayServer()
            .setTransactionToContext()
    }

    private fun startWithTransactionId(): Mono<String> {
        return Mono.deferContextual { Mono.just(it["transactionId"]) }
    }

    private fun <T> Mono<T>.existBuyer(userId: Long): Mono<UserGetByIdRes> {
        return this.flatMap {
            identityWebClient.get()
                .uri("/v1/users/$userId")
                .exchangeToMono {
                    if (it.statusCode().is2xxSuccessful) {
                        return@exchangeToMono it.bodyToMono<UserGetByIdRes>()
                    }
                    if (it.statusCode().is4xxClientError) {
                        return@exchangeToMono it.createError<UserGetByIdRes>()
                            .onErrorMap { IllegalArgumentException("Cannot find user by id \"$userId\"") }
                    }
                    it.createError()
                }
        }
    }

    private fun Mono<ProductRes>.existSeller(): Mono<ProductRes> {
        return this.flatMap { productRes ->
            identityWebClient.get()
                .uri("/v1/users/${productRes.sellerId}")
                .exchangeToMono {
                    if (it.statusCode().is2xxSuccessful) {
                        return@exchangeToMono it.bodyToMono<UserGetByIdRes>()
                    }
                    if (it.statusCode().is4xxClientError) {
                        return@exchangeToMono it.createError<UserGetByIdRes>()
                            .onErrorMap { IllegalArgumentException("Cannot find user by id \"${productRes.sellerId}\"") }
                    }
                    it.createError()
                }
                .map { productRes }
        }
    }

    private fun <T> Mono<T>.existProduct(productId: Long): Mono<ProductRes> {
        return this.flatMap {
            shopWebClient.get()
                .uri("/v1/products/$productId")
                .exchangeToMono {
                    if (it.statusCode().is2xxSuccessful) {
                        return@exchangeToMono it.bodyToMono<ProductRes>()
                    }
                    if (it.statusCode().is4xxClientError) {
                        return@exchangeToMono it.createError<ProductRes>()
                            .onErrorMap { IllegalArgumentException("Cannot find product by id \"$productId\"") }
                    }
                    it.createError()
                }
        }
    }

    private fun Mono<ProductRes>.order(orderReq: OrderReq): Mono<Order> {
        return this.flatMap {
            orderService.order(orderReq, it)
        }
    }

    private fun Mono<Order>.createAndJoinTransaction(): Mono<Order> {
        return this.flatMap { order ->
            Mono.deferContextual<String> { Mono.just(it["transactionId"]) }
                .flatMap { transactionId ->
                    orderTransactionPublisher.join(
                        transactionId,
                        UndoOrder(order.id, OrderState.FAIL)
                    )
                }
                .map { order }
        }
    }

    private fun Mono<Order>.registerOrderToPayServer(): Mono<Order> {
        return this.flatMap { order ->
            Mono.deferContextual<String> { Mono.just(it["transactionId"]) }
                .flatMap { transactionId ->
                    payWebClient.post()
                        .uri("/v1/pays/orders")
                        .bodyValue(payRegisterOrderReq {
                            this.orderId = order.id
                            this.userId = order.userId
                            this.transactionId = transactionId
                            this.price = order.totalPrice()
                        }.toByteArray())
                        .exchangeToMono {
                            if (it.statusCode().is2xxSuccessful) {
                                return@exchangeToMono Mono.just(it.statusCode().value())
                            }
                            it.createError()
                        }
                }.map { order }
        }
    }

    private fun <T> Mono<T>.setTransactionToContext(): Mono<T> {
        return this.contextWrite {
            it.put("transactionId", transactionIdGenerator.generate())
        }
    }
}
