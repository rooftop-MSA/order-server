package org.rooftop.order.infra.transaction

import com.github.f4b6a3.tsid.TsidFactory
import org.rooftop.order.app.TransactionIdGenerator
import org.springframework.stereotype.Service

@Service
class TsidTransactionIdGenerator : TransactionIdGenerator {

    override fun generate(): String = tsidFactory.create().toLong().toString()

    private companion object {
        private val tsidFactory = TsidFactory.newInstance256(110)
    }
}
