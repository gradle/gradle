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

package org.gradle.launcher.daemon

import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.launcher.daemon.client.DaemonDisappearedException
import org.gradle.launcher.daemon.logging.DaemonMessages
import org.gradle.test.fixtures.server.http.CyclicBarrierHttpServer
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule

class ProcessCrashHandlingIntegrationTest extends DaemonIntegrationSpec {
    @Rule CyclicBarrierHttpServer server = new CyclicBarrierHttpServer()

    @Requires(TestPrecondition.NOT_WINDOWS)
    def "tears down the daemon process when the client disconnects"() {
        buildFile << """
task block << {
    new URL("$server.uri").text
}
"""

        when:
        def build = executer.withTasks("block").start()
        server.waitFor()
        daemons.daemon.assertBusy()
        build.abort().waitForFailure()

        then:
        daemons.daemon.stops()
    }

    def "client logs useful information when daemon crashes"() {
        buildFile << """
task block << {
    new URL("$server.uri").text
}
"""

        when:
        def build = executer.withTasks("block").start()
        server.waitFor()
        daemons.daemon.kill()
        def failure = build.waitForFailure()

        then:
        failure.error.contains("----- Last  20 lines from daemon log file")
        failure.assertHasDescription(DaemonDisappearedException.MESSAGE)
    }

    def "client logs useful information when daemon exits"() {
        given:
        file("build.gradle") << "System.exit(0)"

        when:
        def failure = executer.runWithFailure()

        then:
        failure.error.contains("----- Last  20 lines from daemon log file")
        failure.error.contains(DaemonMessages.DAEMON_VM_SHUTTING_DOWN)
        failure.assertHasDescription(DaemonDisappearedException.MESSAGE)

        and:
        daemons.daemon.stops()
    }
}
