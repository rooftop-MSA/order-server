package org.rooftop.order.infra

import org.rooftop.api.identity.UserGetByTokenRes
import org.rooftop.api.pay.payRegisterOrderReq
import org.rooftop.api.shop.ProductRes
import org.rooftop.netx.api.*
import org.rooftop.order.domain.Order
import org.rooftop.order.domain.OrderService
import org.rooftop.order.domain.data.OrderDto
import org.rooftop.order.domain.data.ProductDto
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

@Configuration
class OrderOrchestratorConfigurer(
    private val orchestratorFactory: OrchestratorFactory,
    private val orderService: OrderService,
    @Qualifier("payWebClient") private val payWebClient: WebClient,
    @Qualifier("shopWebClient") private val shopWebClient: WebClient,
    @Qualifier("identityWebClient") private val identityWebClient: WebClient,
) {

    @Bean
    fun orderOrchestrator(): Orchestrator<OrderDto, Order> {
        return orchestratorFactory.create<OrderDto>("orderOrchestrator")
            .startReactiveWithContext(ExistBuyer(identityWebClient))
            .joinReactive(ExistProduct(shopWebClient))
            .joinReactive(PendingOrder(orderService))
            .commitReactiveWithContext(
                RegisterOrderPayment(payWebClient),
                contextRollback = { _, request -> orderService.rollbackOrder(request.id) }
            )
    }

    class ExistBuyer(private val identityWebClient: WebClient) :
        ContextOrchestrate<OrderDto, Mono<OrderDto>> {

        override fun orchestrate(context: Context, request: OrderDto): Mono<OrderDto> {
            val token = context.decodeContext("token", String::class)
            return identityWebClient.get()
                .uri("/v1/users/tokens")
                .header(HttpHeaders.AUTHORIZATION, token)
                .exchangeToMono {
                    require(!it.statusCode().is4xxClientError) { "Cannot find user by token" }

                    if (it.statusCode().is2xxSuccessful) {
                        return@exchangeToMono it.bodyToMono<UserGetByTokenRes>()
                    }

                    return@exchangeToMono it.createError<UserGetByTokenRes>()
                        .onErrorResume { error ->
                            throw IllegalStateException("Internal server error cause", error)
                        }
                }
                .map { userGetByTokenRes ->
                    require(userGetByTokenRes.id == request.userId) {
                        "Authorization could not be verified because the buyer and token does not matched."
                    }
                    request
                }
        }
    }

    class ExistProduct(private val shopWebClient: WebClient) :
        Orchestrate<OrderDto, Mono<Pair<OrderDto, ProductDto>>> {
        override fun orchestrate(request: OrderDto): Mono<Pair<OrderDto, ProductDto>> {
            return shopWebClient.get()
                .uri("/v1/products/${request.productId}")
                .exchangeToMono {
                    require(!it.statusCode().is4xxClientError) { "Cannot find product by id \"${request.productId}\"" }

                    if (it.statusCode().is2xxSuccessful) {
                        return@exchangeToMono it.bodyToMono<ProductRes>()
                    }

                    it.createError<ProductRes>()
                        .onErrorMap { error ->
                            throw IllegalStateException("Internal server error cause ", error)
                        }
                }.map {
                    request to ProductDto(
                        it.id,
                        it.sellerId,
                        it.title,
                        it.description,
                        it.price,
                        it.quantity
                    )
                }
        }
    }

    class PendingOrder(
        private val orderService: OrderService,
    ) : Orchestrate<Pair<OrderDto, ProductDto>, Mono<Order>> {
        override fun orchestrate(request: Pair<OrderDto, ProductDto>): Mono<Order> {
            return orderService.order(request.first, request.second)
        }

        override fun reified(): TypeReference<Pair<OrderDto, ProductDto>> {
            return object : TypeReference<Pair<OrderDto, ProductDto>>() {}
        }
    }

    class RegisterOrderPayment(
        @Qualifier("payWebClient") private val payWebClient: WebClient,
    ) : ContextOrchestrate<Order, Mono<Order>> {

        override fun orchestrate(context: Context, request: Order): Mono<Order> {
            val token = context.decodeContext("token", String::class)
            return payWebClient.post()
                .uri("/v1/pays/orders")
                .header(HttpHeaders.AUTHORIZATION, token)
                .bodyValue(payRegisterOrderReq {
                    this.orderId = request.id
                    this.userId = request.userId
                    this.price = request.totalPrice()
                }.toByteArray())
                .exchangeToMono {
                    require(!it.statusCode().is4xxClientError) { "Cannot register product cause bad request" }
                    if (it.statusCode().is2xxSuccessful) {
                        return@exchangeToMono Mono.just(it.statusCode().value())
                    }
                    if (it.statusCode().is5xxServerError) {
                        return@exchangeToMono it.createError<Int>()
                            .onErrorMap { throw retryException }
                    }
                    it.createError<Int>()
                        .onErrorMap {
                            throw IllegalStateException("Internal server error cause", it)
                        }
                }
                .retryWhen(Retry.backoff(5L, 1.seconds.toJavaDuration())
                    .jitter(0.5)
                    .filter { it.message == retryException.message }
                )
                .map { request }
        }

        private companion object {
            private val retryException = IllegalStateException("Retryable exception")
        }
    }
}
