package org.rooftop.order.domain

import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import org.rooftop.order.domain.repository.OrderRepository
import org.rooftop.order.domain.repository.R2dbcConfigurer
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest
import org.springframework.test.context.ContextConfiguration
import reactor.test.StepVerifier

@DataR2dbcTest
@DisplayName("OrderRepository 클래스의")
@ContextConfiguration(classes = [R2dbcConfigurer::class])
internal class OrderRepositoryTest(private val orderRepository: OrderRepository) : DescribeSpec({

    describe("findById 메소드는") {
        context("저장된 Order 의 id를 받으면,") {
            val exists = orderRepository.save(order()).block()!!

            it("Order 를 반환한다.") {
                val result = orderRepository.findById(exists.id)

                StepVerifier.create(result)
                    .assertNext {
                        it.shouldBeEqualToIgnoringFields(
                            exists,
                            Order::createdAt,
                            Order::modifiedAt
                        )
                    }
                    .verifyComplete()
            }
        }
    }
}) {

}
