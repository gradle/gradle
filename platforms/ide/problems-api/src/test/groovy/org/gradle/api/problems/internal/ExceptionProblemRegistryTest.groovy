/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.problems.internal


import org.gradle.test.fixtures.concurrent.ConcurrentSpec

import java.util.concurrent.CyclicBarrier

class ExceptionProblemRegistryTest extends ConcurrentSpec {

    def "can store and retrieve problems"() {
        setup:
        def exception = new RuntimeException("Boom")
        def problem = Mock(InternalProblem)
        def registry = new ExceptionProblemRegistry()
        registry.onProblem(exception, problem)

        expect:
        registry.getProblemLocator().findAll(exception) == [problem]
    }

    def "can concurrently associate multiple problems to the same exception"() {
        def registry = new ExceptionProblemRegistry()
        def barrier = new CyclicBarrier(10)
        def exception = new RuntimeException("Boom")

        when:
        async {
            10.times {
                start {
                    def problem = Mock(InternalProblem)
                    barrier.await()
                    registry.onProblem(exception, problem)
                }
            }
        }

        then:
        registry.getProblemLocator().findAll(exception).size() == 10
    }

    def "can concurrently associate problems to different exceptions"() {
        def registry = new ExceptionProblemRegistry()
        def barrier = new CyclicBarrier(10)
        List<Exception> exceptions = (0..10).toList().collect { new RuntimeException("Boom $it") }

        when:
        async {
            10.times {i ->
                start {
                    def exception = exceptions[i]
                    def problem = Mock(InternalProblem)
                    barrier.await()
                    registry.onProblem(exception, problem)
                }
            }
        }

        then:
        10.times {
            assert registry.getProblemLocator().findAll(exceptions[it]).size() == 1
        }
    }

    def "adding problem association and reading data won't result in failure"() {
        def registry = new ExceptionProblemRegistry()
        def barrier = new CyclicBarrier(20)
        def exception = new RuntimeException("Boom")

        when:
        async {
            10.times {
                start {
                    barrier.await()
                    registry.getProblemLocator().findAll(exception)
                }
                start {
                    def problem = Mock(InternalProblem)
                    barrier.await()
                    registry.onProblem(exception, problem)
                }
            }
        }

        then:
        notThrown(Throwable)
    }
}
