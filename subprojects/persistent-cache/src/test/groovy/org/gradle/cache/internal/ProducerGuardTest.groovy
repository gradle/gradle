/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.cache.internal

import org.gradle.api.GradleException
import org.gradle.internal.Factory
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ProducerGuardTest extends ConcurrentSpec {

    def "calls all factories"() {
        given:
        def calls = new AtomicInteger()

        when:
        async {
            100.times {
                start {
                    guard.guardByKey("foo", new Factory() {
                        @Override
                        Object create() {
                            return calls.getAndIncrement()
                        }
                    })
                }
            }
        }

        then:
        calls.get() == 100

        where:
        guard << [ProducerGuard.serial(), ProducerGuard.striped(), ProducerGuard.adaptive(), ProducerGuard.timingOut(10, TimeUnit.SECONDS)]
    }

    def "times out"() {
        given:
        def calls = new AtomicInteger()

        when:
        async {
            2.times {
                start {
                    guard.guardByKey("foo", new Factory() {
                        @Override
                        Object create() {
                            sleep(1000)
                            return calls.getAndIncrement()
                        }
                    })
                }
            }
        }

        then:
        def e = thrown(GradleException)
        e.message == 'Timed out while trying to acquire a lock on: foo'
        calls.get() == 1


        where:
        guard << [ProducerGuard.timingOut(10, TimeUnit.MILLISECONDS)]
    }

    def "does not call factories with the same key concurrently"() {
        def concurrentCalls = new AtomicInteger()

        when:
        async {
            100.times {
                start {
                    guard.guardByKey("foo", new Factory() {
                        @Override
                        Object create() {
                            if (concurrentCalls.getAndIncrement() != 0) {
                                throw new IllegalStateException("Factory called concurrently")
                            }
                            return concurrentCalls.decrementAndGet()
                        }
                    })
                }
            }
        }

        then:
        noExceptionThrown()

        where:
        guard << [ProducerGuard.serial(), ProducerGuard.striped(), ProducerGuard.adaptive(), ProducerGuard.timingOut(10, TimeUnit.SECONDS)]
    }
}
