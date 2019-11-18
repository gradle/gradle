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

import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.daemon.DaemonClientFixture
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

    @ToBeFixedForInstantExecution
    def "canceled daemon is reused when it becomes available"() {
        buildFile << """
            task block {
                doLast {
                    new URL("${getUrl('started')}").text
                    new URL("${getUrl('block')}").text
                }
            }
        """

        given:
        executer.beforeExecute {
            executer.withStackTraceChecksDisabled()
        }
        expectEvent("started")
        expectEvent("block")
        def client = new DaemonClientFixture(executer.withArgument("--debug").withTasks("block").start())
        waitFor("started")
        daemons.daemon.assertBusy()
        client.kill()
        daemons.daemon.becomesCanceled()

        when:
        def build = executer.withTasks("tasks").withArguments("--info").start()
        ConcurrentTestUtil.poll {
            assert build.standardOutput.contains(DaemonMessages.WAITING_ON_CANCELED)
        }
        release("block")

        then:
        build.waitForFinish()

        and:
        daemons.daemons.size() == 1
    }

    @ToBeFixedForInstantExecution
    def "does not attempt to reuse a canceled daemon that is not compatible"() {
        buildFile << """
            task block {
                doLast {
                    new URL("${getUrl('started')}").text

                    // Block indefinitely for the daemon to appear busy
                    new java.util.concurrent.Semaphore(0).acquireUninterruptibly()
                }
            }
        """

        given:
        expectEvent("started")
        def client = new DaemonClientFixture(executer.withTasks("block").withArguments("--debug", "-Dorg.gradle.jvmargs=-Xmx1025m").start())
        waitFor("started")
        daemons.daemon.assertBusy()
        client.kill()
        daemons.daemon.becomesCanceled()

        when:
        def build = executer.withTasks("tasks").withArguments("--info").start()

        then:
        build.waitForFinish()

        and:
        daemons.daemons.size() == 2

        and:
        !build.standardOutput.contains(DaemonMessages.WAITING_ON_CANCELED)
    }

    @ToBeFixedForInstantExecution
    def "starts a new daemon when daemons with canceled builds do not become available"() {
        buildFile << """
            task block {
                doLast {
                    new URL("${getUrl('started')}").text

                    // Block indefinitely for the daemon to appear busy
                    new java.util.concurrent.Semaphore(0).acquireUninterruptibly()
                }
            }
        """

        given:
        expectEvent("started")
        def client = new DaemonClientFixture(executer.withArgument("--debug").withTasks("block").start())
        waitFor("started")
        def canceledDaemon = daemons.daemon
        canceledDaemon.assertBusy()
        client.kill()
        canceledDaemon.becomesCanceled()

        when:
        def build = executer.withTasks("tasks").withArguments("--info").start()
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
    @ToBeFixedForInstantExecution
    def "prefers an idle daemon when daemons with canceled builds are available"() {
        given:
        expectEvent("started1")
        expectEvent("started2")
        buildFile << """
            task block {
                doLast {
                    new URL("${getUrl('started')}\$buildNum").text

                    // Block indefinitely for the daemon to appear busy
                    new java.util.concurrent.Semaphore(0).acquireUninterruptibly()
                }
            }
        """

        // 2 daemons we can cancel
        def client1 = new DaemonClientFixture(executer.withTasks("block").withArguments("--debug", "-PbuildNum=1").start())
        waitFor("started1")
        def canceledDaemon1 = daemons.daemon
        def client2 = new DaemonClientFixture(executer.withTasks("block").withArguments("--debug", "-PbuildNum=2").start())
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
        client1.kill()
        client2.kill()

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

    @ToBeFixedForInstantExecution
    def "handles two clients that attempt to connect to an idle daemon simultaneously"() {
        given:
        succeeds("help")
        buildFile << """
            task block {
                doLast {
                    new URL("${getUrl('started')}\$buildNum").text
                }
            }
        """

        when:
        server.expectConcurrent("started1", "started2")
        def gradle1 = executer.withTasks("block").withArguments("--debug", "-PbuildNum=1").start()
        def gradle2 = executer.withTasks("block").withArguments("--debug", "-PbuildNum=2").start()

        then:
        gradle1.waitForFinish()
        gradle2.waitForFinish()

        and:
        daemons.daemons.size() == 2
    }

    String getUrl(String event) {
        return "http://localhost:${server.port}/${event}"
    }

    void expectEvent(String event) {
        server.expectConcurrent(event, "${event}_release")
    }

    void waitFor(String event) {
        new URL(getUrl("${event}_release")).text
    }

    void release(String event) {
        waitFor(event)
    }
}
