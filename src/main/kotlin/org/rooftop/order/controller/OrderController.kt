package org.rooftop.order.controller

import org.rooftop.api.identity.ErrorRes
import org.rooftop.api.identity.errorRes
import org.rooftop.api.order.OrderConfirmReq
import org.rooftop.api.order.OrderReq
import org.rooftop.api.order.OrderRes
import org.rooftop.api.order.orderRes
import org.rooftop.order.app.OrderConfirmFacade
import org.rooftop.order.app.OrderFacade
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/v1")
class OrderController(
    private val orderFacade: OrderFacade,
    private val orderConfirmFacade: OrderConfirmFacade,
) {

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.OK)
    fun order(@RequestBody orderReq: OrderReq): Mono<OrderRes> {
        return orderFacade.order(orderReq)
            .map {
                orderRes {
                    this.orderId = it.id
                }
            }
    }

    @PostMapping("/orders/confirms")
    @ResponseStatus(HttpStatus.OK)
    fun confirmOrder(@RequestBody orderConfirmReq: OrderConfirmReq): Mono<Unit> {
        return orderConfirmFacade.confirmOrder(orderConfirmReq)
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
