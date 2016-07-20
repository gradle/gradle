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

package org.gradle.launcher.daemon.registry

import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus
import spock.lang.Specification

class DaemonStopEventsTest extends Specification {
    def immediateStop1 = new DaemonStopEvent(new Date(), 1L, DaemonExpirationStatus.IMMEDIATE_EXPIRE, "IMMEDIATE_EXPIRE_REASON")
    def gracefulStop1 = new DaemonStopEvent(new Date(), 1L, DaemonExpirationStatus.GRACEFUL_EXPIRE, "GRACEFUL_EXPIRE_REASON")
    def nullStop1 = new DaemonStopEvent(new Date(), 1L, null, null)
    def gracefulStop2 = new DaemonStopEvent(new Date(), 2L, DaemonExpirationStatus.GRACEFUL_EXPIRE, "GRACEFUL_EXPIRE_REASON")

    def "uniqueRecentDaemonStopEvents() handles stop events with null status and reason"() {
        expect:
        DaemonStopEvents.uniqueRecentDaemonStopEvents([gracefulStop2, nullStop1, gracefulStop1]) == [gracefulStop2, gracefulStop1]
        DaemonStopEvents.uniqueRecentDaemonStopEvents([nullStop1, nullStop1]) == [nullStop1]
    }

    def "uniqueRecentDaemonStopEvents() handles empty lists"() {
        expect:
        DaemonStopEvents.uniqueRecentDaemonStopEvents([]) == []
    }

    def "uniqueRecentDaemonStopEvents() de-duplicates with same PID and severity"() {
        expect:
        DaemonStopEvents.uniqueRecentDaemonStopEvents([immediateStop1, immediateStop1]) == [immediateStop1]
    }

    def "uniqueRecentDaemonStopEvents() returns most severe event given same PIDs"() {
        expect:
        DaemonStopEvents.uniqueRecentDaemonStopEvents([gracefulStop1, immediateStop1]) == [immediateStop1]
    }

    def "uniqueRecentDaemonStopEvents() omits stop events older than 1 hour"() {
        given:
        Calendar calendar = Calendar.getInstance()
        calendar.add(Calendar.HOUR, -1)
        Date oneHourAgo = calendar.time

        expect:
        DaemonStopEvents.uniqueRecentDaemonStopEvents([new DaemonStopEvent(oneHourAgo, 3L, DaemonExpirationStatus.GRACEFUL_EXPIRE, null)]) == []
    }
}
