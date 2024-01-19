package org.rooftop.shop.infra.transaction

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.annotation.DisplayName
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.equals.shouldBeEqual
import org.rooftop.order.app.TransactionIdGenerator
import org.rooftop.order.domain.OrderRollbackEvent
import org.rooftop.order.domain.order
import org.rooftop.order.infra.transaction.*
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import kotlin.time.Duration.Companion.seconds

@DisplayName("OrderTransactionListenerTest 클래스 의")
@ContextConfiguration(
    classes = [
        EventCapture::class,
        RedisContainerConfigurer::class,
        ByteArrayRedisSerializer::class,
        ReactiveRedisConfigurer::class,
        TsidTransactionIdGenerator::class,
        OrderTransactionManager::class,
        OrderTransactionListener::class,
    ]
)
@TestPropertySource("classpath:application.properties")
internal class OrderTransactionListenerTest(
    private val eventCapture: EventCapture,
    private val transactionIdGenerator: TransactionIdGenerator,
    private val transactionPublisher: OrderTransactionManager,
) : DescribeSpec({

    afterEach { eventCapture.clear() }

    describe("subscribeStream 메소드는") {
        context("rollback transaction 이 들어오면,") {

            val transactionId = transactionIdGenerator.generate()

            it("OrderRollbackEvent 를 발행한다.") {
                transactionPublisher.join(transactionId, order()).block()
                transactionPublisher.rollback(transactionId).block()

                eventually(10.seconds) {
                    eventCapture.capturedCount(OrderRollbackEvent::class) shouldBeEqual 1
                }
            }
        }

        context("여러개의 transactionId가 등록되어도 ") {

            val transactionId1 = transactionIdGenerator.generate()
            val transactionId2 = transactionIdGenerator.generate()

            it("동시에 요청을 읽을 수 있다.") {
                transactionPublisher.join(transactionId1, order()).block()
                transactionPublisher.join(transactionId2, order()).block()

                transactionPublisher.rollback(transactionId1).block()
                transactionPublisher.rollback(transactionId2).block()

                eventually(10.seconds) {
                    eventCapture.capturedCount(OrderRollbackEvent::class) shouldBeEqual 2
                }
            }
        }

        context("몇초가 흐른후 transaction이 rollback되어도") {

            val transactionId1 = transactionIdGenerator.generate()
            val transactionId2 = transactionIdGenerator.generate()

            it("등록된 transaction을 읽을 수 있다.") {
                transactionPublisher.join(transactionId1, order()).block()
                transactionPublisher.join(transactionId2, order()).block()

                Thread.sleep(5000)

                transactionPublisher.rollback(transactionId1).block()
                transactionPublisher.rollback(transactionId2).block()

                eventually(10.seconds) {
                    eventCapture.capturedCount(OrderRollbackEvent::class) shouldBeEqual 2
                }
            }
        }
    }

})
