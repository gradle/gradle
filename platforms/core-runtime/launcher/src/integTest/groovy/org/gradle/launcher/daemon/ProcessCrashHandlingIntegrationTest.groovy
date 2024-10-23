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

import org.gradle.integtests.fixtures.daemon.DaemonClientFixture
import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.launcher.daemon.client.DaemonDisappearedException
import org.gradle.launcher.daemon.logging.DaemonMessages
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Rule

class ProcessCrashHandlingIntegrationTest extends DaemonIntegrationSpec {
    @Rule BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        server.start()
    }

    def "tears down the daemon process when the client disconnects and build does not cancel in a timely manner"() {
        buildFile << """
            task block {
                doLast {
                    ${server.callFromBuild("block")}
                }
            }
        """

        when:
        def block = server.expectAndBlock("block")
        def client = new DaemonClientFixture(executer.withArgument("--debug").withTasks("block").start())
        block.waitForAllPendingCalls()
        daemons.daemon.assertBusy()
        client.kill()

        then:
        daemons.daemon.becomesCanceled()

        and:
        daemons.daemon.stops()
    }

    def "daemon is idle after the client disconnects and build cancels in a timely manner"() {
        buildFile << """
            task block {
                doLast {
                    ${server.callFromBuild("block")}
                }
            }
        """

        when:
        def block = server.expectAndBlock("block")
        def client = new DaemonClientFixture(executer.withArgument("--debug").withTasks("block").start())
        block.waitForAllPendingCalls()
        daemons.daemon.assertBusy()
        client.kill()

        then:
        daemons.daemon.becomesCanceled()

        when:
        block.releaseAll()

        then:
        daemons.daemon.becomesIdle()

        and:
        daemons.daemon.log.contains(DaemonMessages.CANCELED_BUILD)
    }

    /**
     * When the daemon is started on *nix, we need to detach it from the terminal session of the parent process,
     * otherwise if a ctrl-c is entered on the terminal, it will kill all processes in the session.  This test compiles
     * a native executable that can retrieve the session id of a process so that we can verify that the session id
     * of the daemon is different than the session id of the client.
     */
    @Requires([UnitTestPreconditions.NotWindows])
    def "session id of daemon is different from daemon client"() {
        given:
        withGetSidProject()
        succeeds(":getSid:install")
        buildFile << """
            task block {
                doLast {
                    ${server.callFromBuild("block")}
                }
            }
        """

        when:
        def block = server.expectAndBlock("block")
        DaemonClientFixture client = new DaemonClientFixture(executer.withArgument("--debug").withTasks("block").start())
        block.waitForAllPendingCalls()
        daemons.daemon.assertBusy()

        then:
        def clientSid = getSid(client.process.pid)
        def daemonSid = getSid(daemons.daemon.context.pid)
        clientSid != daemonSid

        cleanup:
        block.releaseAll()
    }

    /**
     * When the daemon is started on Windows, we need to detach from the console that started the process, otherwise if
     * a ctrl-c is entered in that console, it will send a kill signal to all processes attached to the console.  This
     * test compiles a native executable that attempts to connect to the console of a given pid.  We use this to verify
     * that the daemon is not attached to any console.  Note that this is the only way to validate this in Windows since
     * there is no way to determine which console (if any) a process is attached to.  We really only have a system call
     * that allows us to attach to the same console some other process is attached to.  If that process is not attached
     * to any console, we get a specific error that we check for.
     */
    @Requires(UnitTestPreconditions.Windows)
    def "daemon is not attached to a console"() {
        given:
        withAttachConsoleProject()
        succeeds(":attachConsole:install")
        buildFile << """
            task block {
                doLast {
                    ${server.callFromBuild("block")}
                }
            }
        """

        when:
        def block = server.expectAndBlock("block")
        executer.withTasks("block").start()
        block.waitForAllPendingCalls()
        daemons.daemon.assertBusy()

        then:
        getConsole(daemons.daemon.context.pid) == "none"

        cleanup:
        block.releaseAll()
    }

    def "client logs useful information when daemon crashes"() {
        buildFile << """
            task block {
                doLast {
                    ${server.callFromBuild("block")}
                }
            }
        """

        when:
        executer.withStackTraceChecksDisabled() // daemon log may contain stack traces
        def block = server.expectAndBlock("block")
        def build = executer.withTasks("block").start()
        block.waitForAllPendingCalls()
        daemons.daemon.kill()
        def failure = build.waitForFailure()

        then:
        failure.assertHasErrorOutput("----- Last 20 lines from daemon log file")
        failure.assertHasDescription(DaemonDisappearedException.MESSAGE)
    }

    def "client logs location of crash log on daemon crash"() {
        buildFile << """
            task block {
                doLast {
                    ${server.callFromBuild("block")}
                    def theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe")
                    theUnsafe.setAccessible(true)
                    theUnsafe.get(null).getByte(0)
                }
            }
        """

        when:
        executer.withStackTraceChecksDisabled() // daemon log may contain stack traces
        executer.noDaemonCrashChecks()
        def block = server.expectAndBlock("block")
        def build = executer.withTasks("block").start()
        block.waitForAllPendingCalls()
        block.releaseAll()
        def failure = build.waitForFailure()

        then:
        failure.assertHasDescription(DaemonDisappearedException.MESSAGE)
        failure.assertHasErrorOutput("JVM crash log found: file://")
    }

    def "client logs useful information when daemon exits"() {
        given:
        file("build.gradle") << "System.exit(0)"

        when:
        executer.withStackTraceChecksDisabled() // daemon log may contain stack traces
        def failure = executer.runWithFailure()

        then:
        failure.assertHasErrorOutput("----- Last 20 lines from daemon log file")
        failure.assertHasErrorOutput(DaemonMessages.DAEMON_VM_SHUTTING_DOWN)
        failure.assertHasDescription(DaemonDisappearedException.MESSAGE)

        and:
        daemons.daemon.stops()
    }

    String getSid(Long pid) {
        return file("getSid/build/install/getSid/lib/getSid").exec(pid as String).out.trim()
    }

    String getConsole(Long pid) {
        return file("attachConsole/build/install/attachConsole/attachConsole.bat").exec(pid as String).out.trim()
    }

    void withAttachConsoleProject() {
        withProject("attachConsole", """
            #include <stdio.h>
            #include <stdlib.h>
            #include <windows.h>

            // Convenience method for getting a human readable form of the last error.
            void ErrorExit(const char *func){
                DWORD errCode = GetLastError();
                char *err;
                if (!FormatMessage(FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM,
                                   NULL,
                                   errCode,
                                   MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT), // default language
                                   (LPTSTR) &err,
                                   0,
                                   NULL))
                    return;

                static char buffer[1024];
                _snprintf(buffer, sizeof(buffer), "ERROR: %s failed with error: %s\\n", func, err);
                printf("%s", buffer);
                exit(1);
            }

            int main (int argc, char *argv[]) {
                long pid;
                if (argc != 2) {
                    printf("Expected a pid parameter, but was given %d\\\\n", argc - 1);
                    return 1;
                } else {
                    char *ptr;
                    pid = strtoul(argv[1], &ptr, 10);
                }

                // We cannot attach to more than one console at a time, so we
                // need to make sure we are detached first
                if (!FreeConsole()) {
                    if (GetLastError() != ERROR_INVALID_PARAMETER) {
                        ErrorExit(TEXT("FreeConsole"));
                    }
                }

                // AttachConsole also returns ERROR_GEN_FAILURE when the process
                // doesn't exist, so we need to verify that the pid is valid.
                if (OpenProcess(SYNCHRONIZE, FALSE, pid) == NULL) {
                    ErrorExit(TEXT("OpenProcess"));
                }

                // We expect AttachConsole to fail with a particular error if the
                // provided pid is not attached to a console
                // when pid is not attached to console, GetLastError(pid) returns:
                // ERROR_GEN_FAILURE on Win7
                // ERROR_INVALID_HANDLE on Win10
                if (!AttachConsole(pid)) {
                    if (GetLastError() == ERROR_GEN_FAILURE || GetLastError() == ERROR_INVALID_HANDLE) {
                        printf("none\\n");
                        exit(0);
                    } else {
                        ErrorExit(TEXT("AttachConsole"));
                    }
                }

                // Otherwise we return the pid to signify that we were able to attach to its console.
                // In the context of the test, this would be considered a "failure" as we expect to find
                // the daemon not to have a console that we can attach to.
                printf("%d\\n", pid);
                exit(0);
            }
        """)
    }

    void withGetSidProject() {
        withProject("getSid", """
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
        """)
    }

    void withProject(exeName, source) {
        file("${exeName}/src/${exeName}/c/${exeName}.c") << source
        file("${exeName}/build.gradle") << """
            apply plugin: 'org.gradle.c'

            model {
                components {
                    ${exeName}(NativeExecutableSpec) {
                    }
                }
            }
        """
        file('settings.gradle') << """
            include(':${exeName}')
        """
    }
}
