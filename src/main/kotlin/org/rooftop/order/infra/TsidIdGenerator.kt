package org.rooftop.order.infra

import com.github.f4b6a3.tsid.TsidFactory
import org.rooftop.order.domain.IdGenerator
import org.springframework.stereotype.Service
import java.util.stream.Collectors
import java.util.stream.IntStream

@Service
internal class TsidIdGenerator : IdGenerator {

    override fun generate(): Long {
        val factoryId = Thread.currentThread().threadId().toInt() % MAX_THREAD_ID
        val tsidFactory = tsidFactories[factoryId]
            ?: throw IllegalStateException("Cannot find right id generator \"$factoryId\"")

        return tsidFactory.create().toLong()
    }

    private companion object {
        private const val MAX_THREAD_ID = 250
        private val tsidFactories: Map<Int, TsidFactory> = mapOf(
            *IntStream.range(0, MAX_THREAD_ID + 1)
                .mapToObj { factoryId -> factoryId to TsidFactory.newInstance256(factoryId) }
                .collect(Collectors.toList())
                .toTypedArray()
        )
    }
}
