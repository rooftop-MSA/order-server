package org.rooftop.order.app

fun interface TransactionIdGenerator {

    fun generate(): String
}
