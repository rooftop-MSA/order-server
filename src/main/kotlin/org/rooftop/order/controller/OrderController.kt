package org.rooftop.order.controller

import org.rooftop.api.identity.ErrorRes
import org.rooftop.api.identity.errorRes
import org.rooftop.api.order.OrderReq
import org.rooftop.api.order.OrderRes
import org.rooftop.api.order.orderRes
import org.rooftop.order.app.OrderFacade
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/v1")
class OrderController(
    private val orderFacade: OrderFacade,
) {

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.OK)
    fun order(
        @RequestHeader(HttpHeaders.AUTHORIZATION) token: String,
        @RequestBody orderReq: OrderReq,
    ): Mono<OrderRes> {
        return orderFacade.order(token, orderReq)
            .map {
                orderRes {
                    this.orderId = it.id
                }
            }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(illegalArgumentException: IllegalArgumentException): Mono<ErrorRes> {
        return Mono.fromCallable {
            errorRes {
                this.message = illegalArgumentException.message!!
            }
        }
    }

}
