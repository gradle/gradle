/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.model


import org.gradle.api.internal.tasks.NodeExecutionContext
import org.gradle.internal.Describables
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.work.TestWorkerLeaseService

import java.util.concurrent.atomic.AtomicInteger

class CalculatedValueContainerTest extends ConcurrentSpec {
    def projectLeaseService = new TestWorkerLeaseService()

    def "can create container with fixed value"() {
        def container = new CalculatedValueContainer(Describables.of("thing"), "value")

        expect:
        container.get() == "value"
        container.getValue().get() == "value"
    }

    def "calculates and caches value"() {
        def calculator = Mock(ValueCalculator)

        when:
        def container = new CalculatedValueContainer(Describables.of("<thing>"), calculator, projectLeaseService, Stub(NodeExecutionContext))

        then:
        0 * _

        when:
        container.run(Stub(NodeExecutionContext))

        then:
        1 * calculator.calculateValue(_) >> "result"
        0 * _

        when:
        def result = container.get()
        def result2 = container.getValue().get()

        then:
        result == "result"
        result2 == "result"

        and:
        0 * _
    }

    def "retains and rethrows failure to calculate value"() {
        def failure = new RuntimeException()
        def calculator = Mock(ValueCalculator)

        when:
        def container = new CalculatedValueContainer(Describables.of("<thing>"), calculator, projectLeaseService, Stub(NodeExecutionContext))

        then:
        0 * _

        when:
        // NOTE: does not rethrow exception here
        container.run(Stub(NodeExecutionContext))

        then:
        1 * calculator.calculateValue(_) >> { throw failure }
        0 * _

        when:
        def result = container.getValue()

        then:
        result.failure.get() == failure

        and:
        0 * _

        when:
        container.get()

        then:
        def e = thrown(RuntimeException)
        e == failure

        and:
        0 * _
    }

    def "cannot get value before it has been calculated"() {
        def calculator = Mock(ValueCalculator)
        def container = new CalculatedValueContainer(Describables.of("<thing>"), calculator, projectLeaseService, Stub(NodeExecutionContext))

        when:
        container.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Value for <thing> has not been calculated yet.'

        when:
        container.getValue().get()

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'Value for <thing> has not been calculated yet.'
    }

    def "at most one thread calculates the value"() {
        // Don't use a spock mock as these apply their own synchronization
        def container = new CalculatedValueContainer(Describables.of("<thing>"), new Calculator(), projectLeaseService, Stub(NodeExecutionContext))

        when:
        async {
            10.times {
                start {
                    container.finalizeIfNotAlready()
                    assert container.get() == 1
                }
            }
        }

        then:
        container.get() == 1
    }

    def "threads that attempt to calculate the value block until after value is finalized"() {
        // Don't use a spock mock as these apply their own synchronization
        def container = new CalculatedValueContainer(Describables.of("<thing>"), new DelayingCalculator(this), projectLeaseService, Stub(NodeExecutionContext))

        when:
        async {
            start {
                instant.start1
                container.finalizeIfNotAlready()
                instant.finish1
                assert container.get() == 1
            }
            start {
                instant.start2
                container.finalizeIfNotAlready()
                instant.finish2
                assert container.get() == 1
            }
            start {
                instant.start3
                container.finalizeIfNotAlready()
                instant.finish3
                assert container.get() == 1
            }
        }

        then:
        container.get() == 1
        instant.finish1 > instant.finishCalculation
        instant.finish2 > instant.finishCalculation
        instant.finish3 > instant.finishCalculation
    }

    static class Calculator implements ValueCalculator<Integer> {
        private final AtomicInteger value = new AtomicInteger()

        @Override
        Integer calculateValue(NodeExecutionContext context) {
            def changed = value.compareAndSet(0, 1)
            assert changed
            return 1
        }
    }

    class DelayingCalculator extends Calculator {

        private final ConcurrentSpec spec

        DelayingCalculator(ConcurrentSpec spec) {
            this.spec = spec
        }

        @Override
        Integer calculateValue(NodeExecutionContext context) {
            spec.thread.blockUntil.start1
            spec.thread.blockUntil.start2
            spec.thread.blockUntil.start3
            spec.thread.block()
            spec.instant.finishCalculation
            return super.calculateValue(context)
        }
    }
}
