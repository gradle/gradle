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
import org.gradle.test.fixtures.server.http.CyclicBarrierHttpServer
import org.junit.Rule

class DaemonReuseIntegrationTest extends DaemonIntegrationSpec {
    @Rule CyclicBarrierHttpServer server = new CyclicBarrierHttpServer()

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

    def "canceled daemon is reused when it becomes available"() {
        buildFile << """
            task block << {
                new URL("$server.uri").text
            }
        """

        given:
        def build = executer.withTasks("block").start()
        server.waitFor()
        daemons.daemon.assertBusy()
        build.abort().waitForFailure()
        daemons.daemon.becomesCanceled()

        when:
        build = executer.withTasks("tasks").withArguments("--info").start()
        ConcurrentTestUtil.poll {
            assert build.standardOutput.contains(DaemonMessages.WAITING_ON_CANCELED)
        }
        server.release()

        then:
        build.waitForFinish()

        and:
        daemons.daemons.size() == 1
    }

    def "does not attempt to reuse a canceled daemon that is not compatible"() {
        buildFile << """
            task block << {
                new URL("$server.uri").text
            }
        """

        given:
        def build = executer.withTasks("block").withArguments("-Dorg.gradle.jvmargs=-Xmx1025m").start()
        server.waitFor()
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

    def "starts a new daemon when daemons with canceled builds do not become available"() {
        buildFile << """
            task block << {
                new URL("$server.uri").text
            }
        """

        given:
        def build = executer.withTasks("block").start()
        server.waitFor()
        daemons.daemon.assertBusy()
        build.abort().waitForFailure()
        daemons.daemon.becomesCanceled()

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

    def "prefers an idle daemon when daemons with canceled builds are available"() {
        given:
        buildFile << """
            task block << {
                new URL("$server.uri").text
            }
            task markAndBlock << {
                file('started') << "started"
                new URL("$server.uri").text
            }
        """
        def build1 = executer.withTasks("block").start()
        server.waitFor()
        def build2 = executer.withTasks("tasks").start()

        when:
        build2.waitForFinish()

        then:
        assertDaemonCount(1) { it.assertIdle() }
        assertDaemonCount(1) { it.assertBusy() }

        when:
        build1.abort().waitForFailure()

        then:
        ConcurrentTestUtil.poll {
            assertDaemonCount(1) { it.assertCanceled() }
        }

        when:
        executer.withTasks("markAndBlock").start()
        ConcurrentTestUtil.poll {
            assert file('started').exists()
        }

        then:
        daemons.daemons.size() == 2

        and:
        assertDaemonCount(1) { it.assertCanceled() }
        assertDaemonCount(1) { it.assertBusy() }
    }

    /**
     * Assert that exactly a given number of daemons match a condition
     *
     * @param expected - number of daemons that should match
     * @param closure - the condition to check for each daemon - this should throw an exception if a daemon does not match
     */
    void assertDaemonCount(int expected, Closure closure) {
        int count = 0
        daemons.daemons.each { daemon ->
            try {
                closure(daemon)
                count++
            } catch (Throwable t) {
                // does not match
            }
        }
        assert count == expected
    }
}
