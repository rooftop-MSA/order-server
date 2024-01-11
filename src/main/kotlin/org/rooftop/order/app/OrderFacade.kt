package org.rooftop.order.app

import org.rooftop.api.identity.UserGetByNameRes
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
    }

    private fun startWithTransactionId(): Mono<String> {
        return Mono.deferContextual<String> { Mono.just(it["transactionId"]) }
            .contextWrite {
                it.put("transactionId", transactionIdGenerator.generate())
            }
    }

    private fun <T> Mono<T>.existBuyer(userId: Long): Mono<UserGetByNameRes> {
        return this.existUser(userId)
    }

    private fun Mono<ProductRes>.existSeller(): Mono<ProductRes> {
        return this.flatMap { productRes ->
            existUser(productRes.sellerId)
                .map { productRes }
        }
    }

    private fun <T> Mono<T>.existUser(userId: Long): Mono<UserGetByNameRes> {
        return this.flatMap {
            identityWebClient.get()
                .uri("/v1/users/$userId")
                .exchangeToMono {
                    if (it.statusCode().is2xxSuccessful) {
                        return@exchangeToMono it.bodyToMono<UserGetByNameRes>()
                    }
                    it.createError()
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
                            this.transactionId = transactionId
                        }.toByteArray())
                        .exchangeToMono {
                            if (it.statusCode().is2xxSuccessful) {
                                return@exchangeToMono it.bodyToMono<Unit>()
                            }
                            it.createError()
                        }
                }.map { order }
        }
    }
}
