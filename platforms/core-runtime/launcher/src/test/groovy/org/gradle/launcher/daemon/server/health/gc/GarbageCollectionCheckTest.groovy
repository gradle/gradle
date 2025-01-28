/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.launcher.daemon.server.health.gc

import org.gradle.internal.time.FixedClock
import spock.lang.Specification

import java.lang.management.GarbageCollectorMXBean
import java.lang.management.ManagementFactory
import java.lang.management.MemoryType

class GarbageCollectionCheckTest extends Specification {
    def "throwables are not propagated out of the run method"() {
        given:
        def check = new GarbageCollectionCheck(
            FixedClock.create(),
            Mock(GarbageCollectorMXBean),
            ManagementFactory.memoryPoolMXBeans.find { it.type == MemoryType.HEAP }.name,
            Mock(SlidingWindow),
            ManagementFactory.memoryPoolMXBeans.find { it.type == MemoryType.NON_HEAP }.name,
            Mock(SlidingWindow)
        )

        when:
        check.run()

        then:
        1 * _ >> { throw throwable }
        noExceptionThrown()

        where:
        throwable              | _
        new Exception()        | _
        new RuntimeException() | _
        new Error()            | _
    }
}
