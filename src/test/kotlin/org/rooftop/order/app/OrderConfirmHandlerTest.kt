package org.rooftop.order.app

import io.kotest.core.spec.style.DescribeSpec
import org.rooftop.order.Application
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(
    classes = [Application::class]
)
internal class OrderConfirmHandlerTest: DescribeSpec({

}) {
}
