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

package org.gradle.launcher.daemon.server.exec

import org.gradle.api.logging.Logger
import org.gradle.launcher.daemon.server.api.DaemonCommandExecution
import org.gradle.launcher.daemon.server.health.DaemonHealthCheck
import org.gradle.launcher.daemon.server.health.DaemonHealthStats
import org.gradle.launcher.daemon.server.stats.DaemonRunningStats
import spock.lang.Specification

class LogAndCheckHealthTest extends Specification {
    def exec = Mock(DaemonCommandExecution)
    def stats = Mock(DaemonHealthStats)
    def healthCheck = Mock(DaemonHealthCheck)
    def runningStats = Mock(DaemonRunningStats)
    def logger = Mock(Logger)
    def tracker = new LogAndCheckHealth(stats, healthCheck, runningStats, logger)

    def "does not track single use daemon"() {
        when:
        tracker.execute(exec)

        then:
        1 * exec.isSingleUseDaemon() >> true
        1 * exec.proceed()
        0 * _
    }

    def "executes health check on first build"() {
        given:
        runningStats.getBuildCount() >> 0

        when:
        tracker.execute(exec)

        then:
        1 * healthCheck.executeHealthCheck()
        1 * logger.info({ it ==~ /Starting build in new daemon \[memory: \d.*]/ })
    }

    def "executes health check on subsequent builds"() {
        given:
        runningStats.getBuildCount() >> 1

        when:
        tracker.execute(exec)

        then:
        1 * healthCheck.executeHealthCheck()
        1 * stats.healthInfo >> "[health info]"
        1 * logger.info("Starting 2nd build in daemon [health info]")
    }
}
