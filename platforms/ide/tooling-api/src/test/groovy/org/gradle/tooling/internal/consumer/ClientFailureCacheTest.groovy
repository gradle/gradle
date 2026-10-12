/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.tooling.internal.consumer

import org.gradle.tooling.internal.consumer.parameters.BuildProgressListenerAdapter
import org.gradle.tooling.internal.protocol.InternalFailure
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class ClientFailureCacheTest extends Specification {

    def "concurrent conversions of a shared protocol failure return the same consumer failure"() {
        def root = internalFailure("java.lang.RuntimeException: boom\n", [])
        def failureCache = new ClientFailureCache()
        def threadCount = 20
        def executor = Executors.newFixedThreadPool(threadCount)
        def start = new CountDownLatch(1)

        when:
        def futures = (1..threadCount).collect {
            executor.submit({
                start.await()
                BuildProgressListenerAdapter.toFailures([root], failureCache).first()
            } as Callable)
        }
        start.countDown()
        def failures = futures.collect { it.get() }

        then:
        failures.every { it.is(failures.first()) }

        cleanup:
        executor.shutdownNow()
    }

    private InternalFailure internalFailure(String own, List<InternalFailure> causes) {
        Stub(InternalFailure) {
            getMessage() >> "msg"
            getOwnDescription() >> own
            getDescription() >> "FULL-NOT-USED"
            getCauses() >> causes
            getProblems() >> []
        }
    }
}
