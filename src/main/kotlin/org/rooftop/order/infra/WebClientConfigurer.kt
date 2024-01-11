package org.rooftop.order.infra

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.web.reactive.function.client.WebClient

@Configuration
@Profile("prod")
class WebClientConfigurer {

    @Bean
    fun identityWebClient(): WebClient = WebClient.create("http://identity.rooftopmsa.org")

    @Bean
    fun shopWebClient(): WebClient = WebClient.create("http://shop.rooftopmsa.org")

    @Bean
    fun payWebClient(): WebClient = WebClient.create("http://pay.rooftopmsa.org")
}
