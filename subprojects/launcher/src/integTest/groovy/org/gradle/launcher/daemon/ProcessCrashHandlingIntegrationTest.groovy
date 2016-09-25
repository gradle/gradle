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

import org.gradle.api.internal.file.TestFiles
import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.launcher.daemon.client.DaemonDisappearedException
import org.gradle.launcher.daemon.logging.DaemonMessages
import org.gradle.test.fixtures.server.http.CyclicBarrierHttpServer
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Ignore

class ProcessCrashHandlingIntegrationTest extends DaemonIntegrationSpec {
    @Rule CyclicBarrierHttpServer server = new CyclicBarrierHttpServer()

    @Ignore
    @Requires(TestPrecondition.NOT_WINDOWS)
    def "tears down the daemon process when the client disconnects and build does not cancel in a timely manner"() {
        buildFile << """
            task block {
                doLast {
                    new URL("$server.uri").text
                }
            }
        """

        when:
        def build = executer.withTasks("block").start()
        server.waitFor()
        daemons.daemon.assertBusy()
        build.abort().waitForFailure()

        then:
        daemons.daemon.becomesCanceled()

        and:
        daemons.daemon.stops()
    }

    @Ignore
    @Requires(TestPrecondition.NOT_WINDOWS)
    def "daemon is idle after the client disconnects and build cancels in a timely manner"() {
        buildFile << """
            task block {
                doLast {
                    new URL("$server.uri").text
                }
            }
        """

        when:
        def build = executer.withTasks("block").start()
        server.waitFor()
        daemons.daemon.assertBusy()
        build.abort().waitForFailure()

        then:
        daemons.daemon.becomesCanceled()

        when:
        server.release()

        then:
        daemons.daemon.becomesIdle()

        and:
        daemons.daemon.log.contains(DaemonMessages.CANCELED_BUILD)
    }

    // TODO: Need a windows equivalent of this test
    @Requires(TestPrecondition.NOT_WINDOWS)
    def "session id of daemon is different from daemon client"() {
        given:
        withGetSidProject()
        succeeds(":getSid:install")
        buildFile << """
            task block {
                doLast {
                    new URL("$server.uri").text
                }
            }
        """

        when:
        args("--debug")
        def build = executer.withTasks("block").start()
        server.waitFor()
        daemons.daemon.assertBusy()

        then:
        def clientSid = getSid(getPidFromOutput(build))
        def daemonSid = getSid(daemons.daemon.context.pid as String)
        clientSid != daemonSid

        cleanup:
        server.release()
    }

    def "client logs useful information when daemon crashes"() {
        buildFile << """
            task block {
                doLast {
                    new URL("$server.uri").text
                }
            }
        """

        when:
        executer.withStackTraceChecksDisabled() // daemon log may contain stack traces
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
        executer.withStackTraceChecksDisabled() // daemon log may contain stack traces
        def failure = executer.runWithFailure()

        then:
        failure.error.contains("----- Last  20 lines from daemon log file")
        failure.error.contains(DaemonMessages.DAEMON_VM_SHUTTING_DOWN)
        failure.assertHasDescription(DaemonDisappearedException.MESSAGE)

        and:
        daemons.daemon.stops()
    }

    String resultOf(String... command) {
        def output = new ByteArrayOutputStream()
        def e = TestFiles.execHandleFactory().newExec()
            .commandLine(command)
            .redirectErrorStream()
            .setStandardOutput(output)
            .workingDir(testDirectory) //does not matter
            .build()
        e.start()
        def result = e.waitForFinish()
        result.rethrowFailure()
        return output.toString()
    }

    String getPidFromOutput(GradleHandle build) {
        def matcher = (build.standardOutput =~ /Executing build [^\s]+ in daemon client \{pid=(\d+)\}/)
        if (matcher.size() > 0) {
            return matcher[0][1]
        } else {
            throw new IllegalStateException("Could not infer pid from test output")
        }
    }

    String getSid(String pid) {
        return resultOf("getSid/build/install/getSid/lib/getSid", pid).trim()
    }

    void withGetSidProject() {
        file('getSid/src/getSid/c/getsid.c') << """
            #include <unistd.h>
            #include <stdio.h>
            #include <stdlib.h>

            int main (int argc, char *argv[]) {
                int pid, sid;
                if (argc != 2) {
                    printf("Expected a pid parameter, but was given %d\\n", argc - 1);
                    return 1;
                } else {
                    pid = atoi(argv[1]);
                }
                pid_t getsid(pid_t pid);
                sid = getsid(pid);
                printf("%d\\n", sid);
                return 0;
            }
        """
        file('getSid/build.gradle') << """
            apply plugin: 'org.gradle.c'

            model {
                components {
                    getSid(NativeExecutableSpec) {
                    }
                }
            }
        """
        file('settings.gradle') << """
            include(':getSid')
        """
    }
}
