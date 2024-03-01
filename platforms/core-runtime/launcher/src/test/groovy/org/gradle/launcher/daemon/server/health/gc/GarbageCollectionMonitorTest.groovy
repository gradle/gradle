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

package org.gradle.launcher.daemon.server.health.gc

import spock.lang.Specification

import java.util.concurrent.ScheduledExecutorService

class GarbageCollectionMonitorTest extends Specification {
    ScheduledExecutorService scheduledExecutorService = Mock(ScheduledExecutorService)

    def "does not schedule garbage collection check when GC strategy is unknown" () {
        when:
        new DefaultGarbageCollectionMonitor(GarbageCollectorMonitoringStrategy.UNKNOWN, scheduledExecutorService)

        then:
        0 * scheduledExecutorService.scheduleAtFixedRate(_, _, _, _)
    }

    def "heap stats defaults to empty given unknown garbage collector"() {
        given:
        def monitor = new DefaultGarbageCollectionMonitor(GarbageCollectorMonitoringStrategy.UNKNOWN, scheduledExecutorService)

        when:
        monitor.getHeapStats()

        then:
        noExceptionThrown()
    }

    def "non-heap stats defaults to empty given unknown garbage collector"() {
        given:
        def monitor = new DefaultGarbageCollectionMonitor(GarbageCollectorMonitoringStrategy.UNKNOWN, scheduledExecutorService)

        when:
        monitor.getNonHeapStats()

        then:
        noExceptionThrown()
    }
}
