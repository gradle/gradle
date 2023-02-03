/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.internal.concurrent

import org.gradle.test.fixtures.concurrent.ConcurrentSpec

class ServiceLifecycleTest extends ConcurrentSpec {
    def lifecycle = new ServiceLifecycle("[service]")

    def "can use service when not stopped"() {
        def action = Mock(Runnable)

        when:
        lifecycle.use(action)

        then:
        1 * action.run()
        0 * _._
    }

    def "can use service concurrently from multiple threads"() {
        given:
        def action1 = {
            instant.action1Started
            thread.blockUntil.action2Done
            instant.action1Done
        }
        def action2 = {
            thread.blockUntil.action1Started
            instant.action2Done
        }

        when:
        async {
            start {
                lifecycle.use(action1)
            }
            start {
                lifecycle.use(action2)
            }
        }

        then:
        instant.action1Done > instant.action2Done
    }

    def "can stop multiple times"() {
        when:
        lifecycle.stop()
        lifecycle.stop()
        lifecycle.requestStop()
        lifecycle.requestStop()
        lifecycle.stop()

        then:
        noExceptionThrown()
    }

    def "throws exception when attempting to use service after it has stopped"() {
        when:
        lifecycle.stop()
        lifecycle.use { }

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot use [service] as it has been stopped.'
    }

    def "throws exception when attempting to use service after stop has been requested"() {
        when:
        lifecycle.requestStop()
        lifecycle.use { }

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot use [service] as it has been stopped.'
    }

    def "throws exception when attempting to use service while it is stopping due to request stop"() {
        when:
        async {
            start {
                lifecycle.use {
                    lifecycle.requestStop()
                    instant.stopRequested
                    thread.block()
                }
            }
            thread.blockUntil.stopRequested
            lifecycle.use {}
        }

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot use [service] as it is currently stopping.'
    }

    def "throws exception when attempting to use service while it is stopping"() {
        when:
        async {
            start {
                thread.blockUntil.running
                lifecycle.stop()
            }
            start {
                lifecycle.use {
                    instant.running
                    thread.blockUntil.failure
                }
            }
            operation.failure {
                thread.blockUntil.running
                thread.block()
                lifecycle.use {}
            }
        }

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot use [service] as it is currently stopping.'
    }

    def "stop() blocks while service is in use"() {
        when:
        async {
            start {
                lifecycle.use {
                    instant.running
                    thread.block()
                    instant.finished
                }
            }
            thread.blockUntil.running
            lifecycle.stop()
            instant.stopped
        }

        then:
        instant.finished < instant.stopped
    }

    def "multiple threads can call stop() concurrently"() {
        expect:
        async {
            start {
                lifecycle.use {
                    instant.running
                    thread.block()
                }
            }
            2.times {
                start {
                    thread.blockUntil.running
                    lifecycle.stop()
                }
            }
        }
    }

    def "requestStop() does not block while service is in use"() {
        when:
        async {
            start {
                lifecycle.use {
                    instant.running
                    thread.blockUntil.requested
                    instant.finished
                }
            }
            thread.blockUntil.running
            lifecycle.requestStop()
            instant.requested
        }

        then:
        instant.requested < instant.finished
    }

    def "cannot call stop() from thread that is using service"() {
        when:
        lifecycle.use {
            lifecycle.stop()
        }

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot stop [service] from a thread that is using it.'
    }

    def "usage is re-entrant"() {
        when:
        async {
            start {
                lifecycle.use {
                    lifecycle.use {
                        instant.running
                        thread.block()
                    }
                    thread.block()
                    instant.finished
                }
            }
            thread.blockUntil.running
            lifecycle.stop()
            instant.stopped
        }

        then:
        instant.finished < instant.stopped
    }

    def "can call requestStop() from thread that is using service"() {
        when:
        async {
            start {
                lifecycle.use {
                    lifecycle.requestStop()
                    instant.stopRequested
                    thread.block()
                    instant.finished
                }
            }

            thread.blockUntil.stopRequested
            lifecycle.stop()
            instant.stopped
        }

        then:
        instant.finished < instant.stopped
    }
}
