package org.rooftop.order.infra.transaction

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule
import org.rooftop.order.app.UndoOrder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer

@Configuration
class ReactiveRedisConfigurer(
    private val byteArrayRedisSerializer: ByteArrayRedisSerializer,
    @Value("\${distributed.transaction.host.undo-server}") private val undoServerHost: String,
    @Value("\${distributed.transaction.port.undo-server}") private val undoServerPort: String,
    @Value("\${distributed.transaction.host.transaction-server}") private val transactionServerHost: String,
    @Value("\${distributed.transaction.port.transaction-server}") private val transactionServerPort: String,
) {

    @Bean
    fun transactionServer(): ReactiveRedisTemplate<String, ByteArray> {
        val builder = RedisSerializationContext.newSerializationContext<String, ByteArray>(
            StringRedisSerializer()
        )

        val context = builder.value(byteArrayRedisSerializer).build()

        return ReactiveRedisTemplate(transactionServerConnectionFactory(), context)
    }

    @Bean
    fun transactionServerConnectionFactory(): ReactiveRedisConnectionFactory {
        val transactionServerPort: String =
            System.getProperty("distributed.transaction.port.transaction-server")
                ?: transactionServerPort

        return LettuceConnectionFactory(
            transactionServerHost, transactionServerPort.toInt()
        )
    }

    @Bean
    fun undoServer(): ReactiveRedisTemplate<String, UndoOrder> {
        val builder = RedisSerializationContext.newSerializationContext<String, UndoOrder>(
            StringRedisSerializer()
        )

        val objectMapper = ObjectMapper()
        objectMapper.registerModule(ParameterNamesModule())
        val undoProductJacksonSerializer =
            Jackson2JsonRedisSerializer(objectMapper, UndoOrder::class.java)

        val context = builder.value(undoProductJacksonSerializer).build()

        return ReactiveRedisTemplate(undoServerConnectionFactory(), context)
    }

    @Bean
    fun undoServerConnectionFactory(): ReactiveRedisConnectionFactory {
        val undoServerPort: String =
            System.getProperty("distributed.transaction.port.undo-server")
                ?: undoServerPort

        return LettuceConnectionFactory(undoServerHost, undoServerPort.toInt())
    }
}
