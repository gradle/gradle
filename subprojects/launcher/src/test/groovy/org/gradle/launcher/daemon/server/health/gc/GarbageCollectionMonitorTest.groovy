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
import spock.lang.Unroll

import java.util.concurrent.ScheduledExecutorService

class GarbageCollectionMonitorTest extends Specification {
    ScheduledExecutorService scheduledExecutorService = Mock(ScheduledExecutorService)

    @Unroll
    def "schedules periodic garbage collection checks (#strategy)"() {
        when:
        new GarbageCollectionMonitor(strategy, scheduledExecutorService)

        then:
        1 * scheduledExecutorService.scheduleAtFixedRate(_, _, _, _) >> { args ->
            GarbageCollectionCheck check = args[0] as GarbageCollectionCheck
            assert check.garbageCollector == strategy.garbageCollectorName
            assert check.memoryPools == [ strategy.tenuredPoolName, strategy.permGenPoolName ]
            assert check.events.containsKey(strategy.tenuredPoolName)
            assert check.events.containsKey(strategy.permGenPoolName)
        }

        where:
        strategy << GarbageCollectorMonitoringStrategy.values() - GarbageCollectorMonitoringStrategy.UNKNOWN
    }

    def "does not schedule garbage collection check when GC strategy is unknown" () {
        when:
        new GarbageCollectionMonitor(GarbageCollectorMonitoringStrategy.UNKNOWN, scheduledExecutorService)

        then:
        0 * scheduledExecutorService.scheduleAtFixedRate(_, _, _, _)
    }

    def "tenured stats defaults to empty given unknown garbage collector"() {
        given:
        def monitor = new GarbageCollectionMonitor(GarbageCollectorMonitoringStrategy.UNKNOWN, scheduledExecutorService)

        when:
        monitor.getTenuredStats()

        then:
        noExceptionThrown()
    }

    def "perm gen stats defaults to empty given unknown garbage collector"() {
        given:
        def monitor = new GarbageCollectionMonitor(GarbageCollectorMonitoringStrategy.UNKNOWN, scheduledExecutorService)

        when:
        monitor.getPermGenStats()

        then:
        noExceptionThrown()
    }
}
