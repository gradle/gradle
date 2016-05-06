/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.launcher.daemon.server.health

import org.gradle.launcher.daemon.server.api.DaemonCommandExecution
import org.gradle.launcher.daemon.server.api.DaemonStateControl
import spock.lang.Specification

class DaemonHealthTrackerTest extends Specification {

    def control = Mock(DaemonStateControl)
    def exec = Mock(DaemonCommandExecution) {
        getDaemonStateControl() >> control
    }
    def stats = Mock(DaemonStats)
    def status = Mock(DaemonStatus)
    def logger = Mock(HealthLogger)
    def tracker = new DaemonHealthTracker(stats, status, logger)

    def "tracks start and complete events"() {
        when: tracker.execute(exec)

        then: 1 * stats.buildStarted()
        then: 1 * logger.logHealth(stats, _)
        then: 1 * exec.proceed()
        then: 1 * stats.buildFinished()
    }

    def "does not track single use daemon"() {
        when: tracker.execute(exec)

        then:
        1 * exec.isSingleUseDaemon() >> true
        1 * exec.proceed()
        0 * _
    }

    def "stops after the build when performance goes down"() {
        1 * status.isDaemonTired(stats) >> true

        when: tracker.execute(exec)

        then:
        1 * control.requestStop()
    }

    def "does not stop after the build when performance is acceptable"() {
        1 * status.isDaemonTired(stats) >> false

        when: tracker.execute(exec)

        then:
        0 * control.requestStop()
    }
}
