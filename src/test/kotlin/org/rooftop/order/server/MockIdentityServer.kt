package org.rooftop.order.server

import org.springframework.boot.test.context.TestComponent
import org.springframework.context.annotation.Bean
import org.springframework.web.reactive.function.client.WebClient

@TestComponent
class MockIdentityServer : MockServer() {

    @Bean
    fun identityWebClient(): WebClient = WebClient.create(mockWebSerer.url("").toString())
}
