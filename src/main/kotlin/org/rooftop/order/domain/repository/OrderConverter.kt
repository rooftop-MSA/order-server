package org.rooftop.order.domain.repository

import io.r2dbc.spi.Row
import org.rooftop.order.domain.Order
import org.rooftop.order.domain.OrderProduct
import org.rooftop.order.domain.OrderState
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.springframework.data.r2dbc.mapping.OutboundRow
import java.time.LocalDateTime
import java.time.ZoneId

class OrderConverter {

    @WritingConverter
    class Writer : Converter<Order, OutboundRow> {
        override fun convert(source: Order): OutboundRow = source.toOutboundRow()
    }

    @ReadingConverter
    class Reader : Converter<Row, Order> {
        override fun convert(source: Row): Order {
            return Order(
                id = source["id"] as Long,
                userId = source["user_id"] as Long,
                OrderProduct(
                    productId = source["product_id"] as Long,
                    productQuantity = source["product_quantity"] as Long,
                    totalPrice = source["total_price"] as Long
                ),
                state = OrderState.valueOf(source["state"] as String),
                version = source["version"] as Int,
                createdAt = (source["created_at"] as LocalDateTime).atZone(ZoneId.systemDefault())
                    .toInstant(),
                modifiedAt = (source["modified_at"] as LocalDateTime).atZone(ZoneId.systemDefault())
                    .toInstant(),
            )
        }
    }
}
