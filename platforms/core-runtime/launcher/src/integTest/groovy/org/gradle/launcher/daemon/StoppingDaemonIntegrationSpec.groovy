/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.launcher.daemon.logging.DaemonMessages
import org.gradle.launcher.daemon.server.api.DaemonStoppedException
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.junit.Rule

class StoppingDaemonIntegrationSpec extends DaemonIntegrationSpec {
    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    private static final String UNBLOCK = 'unblock'

    def setup() {
        server.start()
    }

    def "daemon process exits and client logs nice error message when daemon stopped"() {
        buildFile << """
task block {
    doLast {
        ${server.callFromBuild(UNBLOCK)}
    }
}
"""

        when:
        def blockingHandler = server.expectAndBlock(UNBLOCK)
        def build = executer.withTasks("block").start()
        blockingHandler.waitForAllPendingCalls()
        daemons.daemon.assertBusy()
        executer.withArguments("--stop").run()
        def failure = build.waitForFailure()

        then:
        stopsSingleDaemon()
        failure.assertHasDescription(DaemonStoppedException.MESSAGE)

        build.waitForFailure()
    }

    def "can handle multiple concurrent stop requests"() {
        buildFile << """
task block {
    doLast {
        ${server.callFromBuild(UNBLOCK)}
    }
}
"""

        when:
        def blockingHandler = server.expectAndBlock(UNBLOCK)
        def build = executer.withTasks("block").start()
        blockingHandler.waitForAllPendingCalls()

        def stopExecutions = []
        5.times { idx ->
            stopExecutions << executer.withArguments("--stop").start()
        }
        stopExecutions.each { it.waitForFinish() }
        def out = executer.withArguments("--stop").run().output

        then:
        stopsSingleDaemon()
        out.contains(DaemonMessages.NO_DAEMONS_RUNNING)

        build.waitForFailure()
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "can stop a daemon that is using a different java home"() {
        given:
        succeeds()
        daemons.daemon.assertIdle()

        when:
        executer.withJavaHome(AvailableJavaHomes.differentJdk.javaHome)
        executer.withArguments("--stop").run()

        then:
        stopsSingleDaemon()
    }

    def "reports exact number of daemons stopped and keeps console output clean"() {
        given:
        executer.noExtraLogging()
        executer.run()

        when:
        def out = executer.withArguments("--stop").run().normalizedOutput

        then:
        out == '''Stopping Daemon(s)
1 Daemon stopped
'''

        when:
        out = executer.withArguments("--stop").run().normalizedOutput

        then:
        out == """$DaemonMessages.NO_DAEMONS_RUNNING
"""
    }

    private void stopsSingleDaemon() {
        daemons.daemon.stops()
    }
}
