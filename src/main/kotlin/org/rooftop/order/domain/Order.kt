package org.rooftop.order.domain

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceCreator
import org.springframework.data.annotation.Version
import org.springframework.data.r2dbc.mapping.OutboundRow
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.r2dbc.core.Parameter
import java.time.Instant

@Table("orders")
class Order(
    @Id
    @Column("id")
    val id: Long,

    @Column("user_id")
    val userId: Long,

    @Transient
    private val orderProduct: OrderProduct,

    @Column("state")
    private val state: OrderState,

    @Transient
    private val isNew: Boolean = false,

    @Version
    private var version: Int? = null,
    createdAt: Instant? = null,
    modifiedAt: Instant? = null,
) : BaseEntity(isNew, createdAt, modifiedAt) {

    @PersistenceCreator
    constructor(
        id: Long,
        userId: Long,
        orderProduct: OrderProduct,
        state: OrderState,
        version: Int,
        createdAt: Instant,
        modifiedAt: Instant,
    ) : this(id, userId, orderProduct, state, false, version, createdAt, modifiedAt)

    override fun getId(): Long = id

    fun totalPrice(): Long = orderProduct.totalPrice

    fun fail(): Order {
        return Order(
            id = id,
            userId = userId,
            orderProduct = orderProduct,
            state = OrderState.FAILED,
            isNew = isNew,
            version = version,
            createdAt = createdAt,
            modifiedAt = modifiedAt,
        )
    }

    @Suppress("DEPRECATION")
    fun toOutboundRow(): OutboundRow {
        return OutboundRow()
            .append("id", Parameter.from(id))
            .append("user_id", Parameter.from(userId))
            .append("product_id", Parameter.from(orderProduct.productId))
            .append("product_quantity", Parameter.from(orderProduct.productQuantity))
            .append("total_price", Parameter.from(orderProduct.totalPrice))
            .append("state", Parameter.from(state.name))
            .append("version", Parameter.from(version!!))
            .append("created_at", Parameter.from(createdAt!!))
            .append("modified_at", Parameter.from(modifiedAt!!))
    }
}
