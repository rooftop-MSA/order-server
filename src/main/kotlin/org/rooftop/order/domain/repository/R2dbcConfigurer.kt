package org.rooftop.order.domain.repository

import org.rooftop.order.Application
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions
import org.springframework.data.r2dbc.dialect.MySqlDialect
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories

@Configuration
@EnableR2dbcAuditing
@EnableR2dbcRepositories(basePackageClasses = [Application::class])
abstract class R2dbcConfigurer : AbstractR2dbcConfiguration() {

    @Bean
    override fun r2dbcCustomConversions(): R2dbcCustomConversions {
        val converters = listOf(OrderConverter.Reader(), OrderConverter.Writer())
        return R2dbcCustomConversions.of(MySqlDialect.INSTANCE, converters);
    }
}
