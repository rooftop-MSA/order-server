package org.rooftop.order.infra

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.web.reactive.function.client.WebClient

@Configuration
@Profile("prod")
class WebClientConfigurer(
    @Value("\${rooftop.server.identity:http://identity.rooftopmsa.org}") private val identityServerUri: String,
    @Value("\${rooftop.server.shop:http://shop.rooftopmsa.org}") private val shopServerUri: String,
    @Value("\${rooftop.server.pay:http://pay.rooftopmsa.org}") private val payServerUri: String,
) {

    @Bean
    fun identityWebClient(): WebClient = WebClient.create(identityServerUri)

    @Bean
    fun shopWebClient(): WebClient = WebClient.create(shopServerUri)

    @Bean
    fun payWebClient(): WebClient = WebClient.create(payServerUri)
}
