package org.rooftop.order.app

import org.rooftop.api.identity.UserGetByIdRes
import org.rooftop.api.identity.UserGetByTokenRes
import org.rooftop.api.order.OrderReq
import org.rooftop.api.pay.payRegisterOrderReq
import org.rooftop.api.shop.ProductRes
import org.rooftop.order.domain.Order
import org.rooftop.order.domain.OrderService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

@Service
class OrderFacade(
    private val orderService: OrderService,
    private val transactionIdGenerator: TransactionIdGenerator,
    private val orderTransactionManager: TransactionManager<Order>,
    @Qualifier("payWebClient") private val payWebClient: WebClient,
    @Qualifier("shopWebClient") private val shopWebClient: WebClient,
    @Qualifier("identityWebClient") private val identityWebClient: WebClient,
) {

    fun order(token: String, orderReq: OrderReq): Mono<Order> {
        return startWithTransactionId()
            .existBuyer(orderReq.userId)
            .isAuthorizedBuyer(token)
            .existProduct(orderReq.productId)
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

    private fun Mono<UserGetByIdRes>.isAuthorizedBuyer(token: String): Mono<UserGetByIdRes> {
        return this.flatMap { buyer ->
            identityWebClient.get()
                .uri("/v1/users/tokens")
                .header(HttpHeaders.AUTHORIZATION, token)
                .exchangeToMono<UserGetByTokenRes?> {
                    if (it.statusCode().is2xxSuccessful) {
                        return@exchangeToMono it.bodyToMono<UserGetByTokenRes>()
                    }
                    if (it.statusCode().is4xxClientError) {
                        return@exchangeToMono it.createError<UserGetByTokenRes>()
                            .onErrorMap {
                                IllegalArgumentException("Cannot find user by token")
                            }
                    }
                    it.createError()
                }
                .map { token ->
                    when (token.id == buyer.id) {
                        true -> buyer
                        false -> throw IllegalArgumentException("Authorization could not be verified because the buyer and token do not match.")
                    }
                }
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
                    orderTransactionManager.join(transactionId, order)
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
