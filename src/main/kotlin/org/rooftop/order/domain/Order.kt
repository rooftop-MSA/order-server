package org.rooftop.order.domain

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceCreator
import org.springframework.data.annotation.Version
import org.springframework.data.r2dbc.mapping.OutboundRow
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.r2dbc.core.Parameter

@Table("order")
class Order(
    @Id
    @Column("id")
    private val id: Long,

    @Transient
    private val orderProduct: OrderProduct,

    @Column("state")
    private val state: OrderState,

    @Transient
    private val isNew: Boolean = false,

    @Version
    private var version: Int? = null,
) : BaseEntity(isNew) {

    @PersistenceCreator
    constructor(
        id: Long,
        orderProduct: OrderProduct,
        state: OrderState,
        version: Int,
    ) : this(id, orderProduct, state, false, version)

    override fun getId(): Long = id

    @Suppress("DEPRECATION")
    fun toOutboundRow(): OutboundRow {
        return OutboundRow()
            .append("id", Parameter.from(id))
            .append("product_id", Parameter.from(orderProduct.productId))
            .append("product_quantity", Parameter.from(orderProduct.productQuantity))
            .append("total_price", Parameter.from(orderProduct.totalPrice))
            .append("state", Parameter.from(state.name))
            .append("version", Parameter.from(version!!))
    }
}
