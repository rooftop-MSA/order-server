package org.rooftop.order.domain

import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import java.time.Instant

abstract class BaseEntity(
    @Transient
    private val isNew: Boolean = false,

    @CreatedDate
    @Column("created_at")
    var createdAt: Instant? = null,

    @LastModifiedDate
    @Column("modified_at")
    var modifiedAt: Instant? = null,
) : Persistable<Long> {

    abstract override fun getId(): Long

    override fun isNew(): Boolean = isNew
}
