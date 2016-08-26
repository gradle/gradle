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
import org.gradle.launcher.daemon.logging.DaemonMessages
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule

class DaemonReuseIntegrationTest extends DaemonIntegrationSpec {
    @Rule BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        server.start()
    }

    def "idle daemon is reused in preference to starting a new daemon"() {
        given:
        executer.run()
        daemons.daemon.assertIdle()

        when:
        5.times {
            executer.run()
        }

        then:
        daemons.daemons.size() == 1
    }

    // GradleHandle.abort() does not work reliably on windows and creates flakiness
    @Requires(TestPrecondition.NOT_WINDOWS)
    def "canceled daemon is reused when it becomes available"() {
        buildFile << """
            task block << {
                new URL("${getUrl('started')}").text
                new URL("${getUrl('block')}").text
            }
        """

        given:
        expectEvent("started")
        expectEvent("block")
        def build = executer.withTasks("block").start()
        waitFor("started")
        daemons.daemon.assertBusy()
        build.abort().waitForFailure()
        daemons.daemon.becomesCanceled()

        when:
        build = executer.withTasks("tasks").withArguments("--info").start()
        ConcurrentTestUtil.poll {
            assert build.standardOutput.contains(DaemonMessages.WAITING_ON_CANCELED)
        }
        release("block")

        then:
        build.waitForFinish()

        and:
        daemons.daemons.size() == 1
    }

    // GradleHandle.abort() does not work reliably on windows and creates flakiness
    @Requires(TestPrecondition.NOT_WINDOWS)
    def "does not attempt to reuse a canceled daemon that is not compatible"() {
        buildFile << """
            task block << {
                new URL("${getUrl('started')}").text
                java.util.concurrent.locks.LockSupport.park()
            }
        """

        given:
        expectEvent("started")
        def build = executer.withTasks("block").withArguments("-Dorg.gradle.jvmargs=-Xmx1025m").start()
        waitFor("started")
        daemons.daemon.assertBusy()
        build.abort().waitForFailure()
        daemons.daemon.becomesCanceled()

        when:
        build = executer.withTasks("tasks").withArguments("--info").start()

        then:
        build.waitForFinish()

        and:
        daemons.daemons.size() == 2

        and:
        !build.standardOutput.contains(DaemonMessages.WAITING_ON_CANCELED)
    }

    // GradleHandle.abort() does not work reliably on windows and creates flakiness
    @Requires(TestPrecondition.NOT_WINDOWS)
    def "starts a new daemon when daemons with canceled builds do not become available"() {
        buildFile << """
            task block << {
                new URL("${getUrl('started')}").text
                java.util.concurrent.locks.LockSupport.park()
            }
        """

        given:
        expectEvent("started")
        def build = executer.withTasks("block").start()
        waitFor("started")
        def canceledDaemon = daemons.daemon
        canceledDaemon.assertBusy()
        build.abort().waitForFailure()
        canceledDaemon.becomesCanceled()

        when:
        build = executer.withTasks("tasks").withArguments("--info").start()
        ConcurrentTestUtil.poll {
            assert build.standardOutput.contains(DaemonMessages.WAITING_ON_CANCELED)
        }

        then:
        build.waitForFinish()

        and:
        daemons.daemons.size() == 2
    }

    // GradleHandle.abort() does not work reliably on windows and creates flakiness
    @Requires(TestPrecondition.NOT_WINDOWS)
    def "prefers an idle daemon when daemons with canceled builds are available"() {
        given:
        expectEvent("started1")
        expectEvent("started2")
        buildFile << """
            task block << {
                new URL("${getUrl('started')}\$buildNum").text
                java.util.concurrent.locks.LockSupport.park()
            }
        """

        // 2 daemons we can cancel
        def build1 = executer.withTasks("block").withArguments("-PbuildNum=1").start()
        waitFor("started1")
        def canceledDaemon1 = daemons.daemon
        def build2 = executer.withTasks("block").withArguments("-PbuildNum=2").start()
        waitFor("started2")
        def canceledDaemon2 = daemons.daemons.find { it.context.pid != canceledDaemon1.context.pid }

        // 1 daemon we can reuse
        def build3 = executer.withTasks("tasks").start()

        when:
        build3.waitForFinish()

        then:
        daemons.daemons.size() == 3
        def idleDaemon = daemons.daemons.find { ! (it.context.pid in [ canceledDaemon1.context.pid, canceledDaemon2.context.pid ]) }
        idleDaemon.assertIdle()
        canceledDaemon1.assertBusy()
        canceledDaemon2.assertBusy()

        when:
        build1.abort().waitForFailure()
        build2.abort().waitForFailure()

        then:
        canceledDaemon1.becomesCanceled()
        canceledDaemon2.becomesCanceled()

        when:
        build3 = executer.withTasks("tasks").start()

        then:
        build3.waitForFinish()

        and:
        daemons.daemons.size() == 3

        and:
        !build3.standardOutput.contains(DaemonMessages.WAITING_ON_CANCELED)
    }

    String getUrl(String event) {
        return "http://localhost:${server.port}/${event}"
    }

    void expectEvent(String event) {
        server.expectConcurrentExecution(event, "${event}_release")
    }

    void waitFor(String event) {
        new URL(getUrl("${event}_release")).text
    }

    void release(String event) {
        waitFor(event)
    }
}
