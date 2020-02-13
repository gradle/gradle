/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.launcher.daemon

import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.launcher.daemon.server.exec.LogToClient

class DaemonLoggingIntegrationTest extends DaemonIntegrationSpec {

    def "can configure daemon logging"() {
        file("gradle.properties") << """
            systemProp.${LogToClient.DAEMON_LOGLEVEL_PROPERTY_NAME}=WARN
        """

        file("build.gradle") << """
            println "Lifecycle"
        """

        when:
        succeeds("help")
        def daemonLog = new DaemonLogLevelFixture(daemons.daemon.logFile)
        then:
        !daemonLog.debugLogLevel
        daemonLog.infoLogLevelBeforeBuildStarts
        !daemonLog.infoLogLevelAfterBuildFinished
        daemonLog.contains("Lifecycle")

        when:
        daemonLog.truncate()
        succeeds("help")
        then:
        !daemonLog.debugLogLevel
        !daemonLog.infoLogLevelBeforeBuildStarts
        !daemonLog.infoLogLevelAfterBuildFinished
        daemonLog.contains("Lifecycle")

        when:
        file("gradle.properties").text = "systemProp.${LogToClient.DAEMON_LOGLEVEL_PROPERTY_NAME}=DEBUG"
        daemonLog.truncate()
        succeeds("help")
        then:
        daemonLog.debugLogLevel
    }

    static class DaemonLogLevelFixture {

        private final File daemonLog

        DaemonLogLevelFixture(File daemonLog) {
            this.daemonLog = daemonLog
        }

        private boolean isDebugLogLevel() {
            daemonLog.text.contains("[DEBUG]")
        }

        private boolean isInfoLogLevelBeforeBuildStarts() {
            daemonLog.text.contains("Marking the daemon as busy")
        }

        private boolean isInfoLogLevelAfterBuildFinished() {
            daemonLog.text.contains("Marking the daemon as idle")
        }

        boolean contains(String expected) {
            daemonLog.text.contains(expected)
        }

        void truncate() {
            daemonLog.text = ""
        }
    }
}
