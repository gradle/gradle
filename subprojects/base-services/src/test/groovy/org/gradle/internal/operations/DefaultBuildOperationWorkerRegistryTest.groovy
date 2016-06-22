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
                def cl = registry.workerStart()
                instant.worker1
                thread.blockUntil.worker2
                cl.workerCompleted()
            }
            start {
                def cl = registry.workerStart()
                instant.worker2
                thread.blockUntil.worker1
                cl.workerCompleted()
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
                def cl = registry.workerStart()
                instant.worker1
                thread.block()
                instant.worker1Finished
                cl.workerCompleted()
            }
            start {
                thread.blockUntil.worker1
                def cl = registry.workerStart()
                instant.worker2
                cl.workerCompleted()
            }
        }

        then:
        instant.worker2 > instant.worker1Finished

        cleanup:
        registry?.stop()
    }
}
