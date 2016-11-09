/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.operations

import org.gradle.test.fixtures.concurrent.ConcurrentSpec

class DefaultBuildOperationWorkerRegistryTest extends ConcurrentSpec {
    def "operation starts immediately when there are sufficient leases available"() {
        def registry = new DefaultBuildOperationWorkerRegistry(2)

        expect:
        async {
            start {
                def cl = registry.operationStart()
                instant.worker1
                thread.blockUntil.worker2
                cl.operationFinish()
            }
            start {
                def cl = registry.operationStart()
                instant.worker2
                thread.blockUntil.worker1
                cl.operationFinish()
            }
        }

        cleanup:
        registry?.stop()
    }

    def "operation start blocks when there are no leases available"() {
        def registry = new DefaultBuildOperationWorkerRegistry(1)

        when:
        async {
            start {
                def cl = registry.operationStart()
                instant.worker1
                thread.block()
                instant.worker1Finished
                cl.operationFinish()
            }
            start {
                thread.blockUntil.worker1
                def cl = registry.operationStart()
                instant.worker2
                cl.operationFinish()
            }
        }

        then:
        instant.worker2 > instant.worker1Finished

        cleanup:
        registry?.stop()
    }

    def "child operation starts immediately when there are sufficient leases available"() {
        def registry = new DefaultBuildOperationWorkerRegistry(1)

        expect:
        async {
            start {
                def cl = registry.operationStart()
                def op = registry.current
                start {
                    def child = op.operationStart()
                    child.operationFinish()
                    instant.childFinished
                }
                thread.blockUntil.childFinished
                cl.operationFinish()
            }
        }

        cleanup:
        registry?.stop()
    }

    def "child operation borrows parent lease"() {
        def registry = new DefaultBuildOperationWorkerRegistry(1)

        expect:
        async {
            start {
                def cl = registry.operationStart()
                def op = registry.current
                start {
                    def child = op.operationStart()
                    child.operationFinish()
                    instant.child1Finished
                }
                thread.blockUntil.child1Finished
                start {
                    def child = op.operationStart()
                    child.operationFinish()
                    instant.child2Finished
                }
                thread.blockUntil.child2Finished
                cl.operationFinish()
            }
        }

        cleanup:
        registry?.stop()
    }

    def "child operations block until lease available when there is more than one child"() {
        def registry = new DefaultBuildOperationWorkerRegistry(1)

        when:
        async {
            start {
                def cl = registry.operationStart()
                def op = registry.current
                start {
                    def child = op.operationStart()
                    instant.child1Started
                    thread.block()
                    instant.child1Finished
                    child.operationFinish()
                }
                start {
                    thread.blockUntil.child1Started
                    def child = op.operationStart()
                    instant.child2Started
                    child.operationFinish()
                    instant.child2Finished
                }
                thread.blockUntil.child2Finished
                cl.operationFinish()
            }
        }

        then:
        instant.child2Started > instant.child1Finished

        cleanup:
        registry?.stop()
    }

    def "fails when child operation completes after parent"() {
        def registry = new DefaultBuildOperationWorkerRegistry(2)

        when:
        async {
            start {
                def cl = registry.operationStart()
                def op = registry.current
                start {
                    def child = op.operationStart()
                    instant.childStarted
                    thread.blockUntil.parentFinished
                    child.operationFinish()
                }
                thread.blockUntil.childStarted
                try {
                    cl.operationFinish()
                } finally {
                    instant.parentFinished
                }
            }
        }

        then:
        IllegalStateException e = thrown()
        e.message == 'Some child operations have not yet completed.'

        cleanup:
        registry?.stop()
    }

    def "can get operation for current thread"() {
        def registry = new DefaultBuildOperationWorkerRegistry(1)

        given:
        def op = registry.operationStart()

        expect:
        registry.current == op

        cleanup:
        op?.operationFinish()
        registry?.stop()
    }

    def "cannot get current operation when current thread has no operation"() {
        def registry = new DefaultBuildOperationWorkerRegistry(1)

        when:
        registry.current

        then:
        IllegalStateException e = thrown()
        e.message == 'No build operation associated with the current thread'

        when:
        registry.operationStart().operationFinish()
        registry.current

        then:
        e = thrown()
        e.message == 'No build operation associated with the current thread'

        cleanup:
        registry?.stop()
    }
}
