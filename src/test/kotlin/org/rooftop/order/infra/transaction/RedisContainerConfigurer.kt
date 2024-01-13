package org.rooftop.shop.infra.transaction

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.core.env.ConfigurableEnvironment
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration
class RedisContainerConfigurer(
    private val environment: ConfigurableEnvironment,
) {

    init {
        val redis: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:7.2.3"))
            .withExposedPorts(6379)

        redis.start()

        System.setProperty(
            "distributed.transaction.port.undo-server",
            redis.getMappedPort(6379).toString()
        )
        System.setProperty(
            "distributed.transaction.port.transaction-server",
            redis.getMappedPort(6379).toString()
        )
    }
}
