package org.rooftop.order.domain.repository

import io.r2dbc.spi.ConnectionFactory
import org.rooftop.order.Application
import org.springframework.context.annotation.Configuration
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions
import org.springframework.data.r2dbc.dialect.MySqlDialect
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories

@Configuration
@EnableR2dbcRepositories(basePackageClasses = [Application::class])
class R2dbcConfigurer(
    private val connectionFactory: ConnectionFactory,
) : AbstractR2dbcConfiguration() {

    override fun r2dbcCustomConversions(): R2dbcCustomConversions {
        val converters = listOf(OrderConverter.Reader(), OrderConverter.Writer())
        return R2dbcCustomConversions.of(MySqlDialect.INSTANCE, converters);
    }

    override fun connectionFactory(): ConnectionFactory = connectionFactory
}
