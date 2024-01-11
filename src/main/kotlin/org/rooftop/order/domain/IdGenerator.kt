package org.rooftop.order.domain

fun interface IdGenerator {

    fun generate(): Long
}
