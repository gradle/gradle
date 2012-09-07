/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.integtests.fixtures.HttpServer
import org.gradle.launcher.daemon.logging.DaemonMessages
import org.gradle.launcher.daemon.testing.DaemonLogsAnalyzer
import org.gradle.tests.fixtures.ConcurrentTestUtil
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Issue

/**
 * by Szczepan Faber, created at: 1/20/12
 */
@Requires(TestPrecondition.UNIX)
//because we can only forcefully kill daemons on Unix atm.
//The implementation is not OS specific, only the test is
// so it's not a big deal it does not run everywhere.
class DaemonInitialCommunicationFailureIntegrationSpec extends DaemonIntegrationSpec {

    def cleanup() {
        stopDaemonsNow()
    }

    @Issue("GRADLE-2444")
    def "behaves if the registry contains connectable port without daemon on the other end"() {
        when:
        buildSucceeds()

        then:
        //there should be one idle daemon
        def daemon = new DaemonLogsAnalyzer(distribution.daemonBaseDir).idleDaemon

        when:
        daemon.kill()

        and:
        //starting some service on the daemon port
        ConcurrentTestUtil.poll {
            new HttpServer().start(daemon.port)
        }

        then:
        //most fundamentally, the build works ok:
        buildSucceeds()

        and:
        output.contains DaemonMessages.REMOVING_DAEMON_ADDRESS_ON_FAILURE

        when:
        buildSucceeds()

        then:
        //suspected address was removed from the registry
        // so we should the client does not attempt to connect to it again
        !output.contains(DaemonMessages.REMOVING_DAEMON_ADDRESS_ON_FAILURE)
    }

    @Issue("GRADLE-2444")
    def "stop() behaves if the registry contains connectable port without daemon on the other end"() {
        when:
        buildSucceeds()

        then:
        def daemon = new DaemonLogsAnalyzer(distribution.daemonBaseDir).idleDaemon

        when:
        daemon.kill()
        ConcurrentTestUtil.poll {
            new HttpServer().start(daemon.port)
        }

        then:
        //most fundamentally, stopping works ok:
        stopDaemonsNow()

        and:
        output.contains DaemonMessages.REMOVING_DAEMON_ADDRESS_ON_FAILURE

        when:
        stopDaemonsNow()

        then:
        !output.contains(DaemonMessages.REMOVING_DAEMON_ADDRESS_ON_FAILURE)
        output.contains(DaemonMessages.NO_DAEMONS_RUNNING)
    }

    @Issue("GRADLE-2464")
    def "when nothing responds to the address found in the registry we remove the address"() {
        when:
        buildSucceeds()

        then:
        def daemon = new DaemonLogsAnalyzer(distribution.daemonBaseDir).idleDaemon

        when:
        daemon.kill()

        then:
        buildSucceeds()

        and:
        output.contains DaemonMessages.REMOVING_DAEMON_ADDRESS_ON_FAILURE
    }
}