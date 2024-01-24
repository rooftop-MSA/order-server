package org.rooftop.order.server

import com.google.protobuf.MessageLite
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.EventListener

abstract class MockServer {

    protected val mockWebSerer: MockWebServer = MockWebServer()

    init {
        mockWebSerer.start()
    }

    fun enqueue200() {
        mockWebSerer.enqueue(MockResponse().setResponseCode(200))
    }

    fun <T : MessageLite> enqueue200(vararg response: T) {
        response.forEach {
            mockWebSerer.enqueue(
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "application/x-protobuf")
                    .setBody(Buffer().write(it.toByteArray()))
            )
        }
    }

    fun enqueue500() {
        mockWebSerer.enqueue(
            MockResponse().setResponseCode(500)
        )
    }

    fun enqueue400() {
        mockWebSerer.enqueue(
            MockResponse().setResponseCode(400)
        )
    }

    @EventListener(ContextClosedEvent::class)
    fun shutDown() = mockWebSerer.shutdown()
}
