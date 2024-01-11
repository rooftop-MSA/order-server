package org.rooftop.order.infra.transaction

import org.springframework.data.redis.serializer.RedisSerializer
import org.springframework.stereotype.Component

@Component
class ByteArrayRedisSerializer: RedisSerializer<ByteArray> {

    override fun serialize(t: ByteArray?): ByteArray? = t

    override fun deserialize(bytes: ByteArray?): ByteArray? = bytes
}
