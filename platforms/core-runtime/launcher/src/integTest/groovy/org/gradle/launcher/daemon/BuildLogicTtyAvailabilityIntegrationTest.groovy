/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.api.internal.classpath.DefaultModuleRegistry
import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.internal.installation.GradleInstallation
import org.gradle.internal.jvm.Jvm
import org.gradle.launcher.daemon.client.DaemonStartupMessage
import org.gradle.launcher.daemon.configuration.DaemonParameters
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.OsTestPreconditions
import org.gradle.test.preconditions.TestExecutionPreconditions

import java.util.concurrent.TimeUnit

/**
 * Verifies that build logic in embedded daemons can access the terminal that started the build.
 * <p>
 * This verifies the niche and technically unsupported use case of accessing the surrounding
 * TTY of the client process from build logic running in an embedded daemon. In practice, there is
 * real build logic out there using this behavior to interactively prompt the user for input.
 * <p>
 * The client process is started under a pseudo-terminal, like a user running Gradle from an
 * interactive shell, since the test JVM itself has no terminal attached.
 */
@Requires(
    value = [TestExecutionPreconditions.NotEmbeddedExecutor, OsTestPreconditions.NotWindows],
    reason = "Explicitly forks gradle clients. Windows does not have a script command to run a process under a pseudo-terminal."
)
class BuildLogicTtyAvailabilityIntegrationTest extends DaemonIntegrationSpec {

    TestFile runUnderTty = writeRunUnderTty()
    TestFile gradleUnderTty = writeGradleUnderTty(runUnderTty)

    def "build logic cannot access the tty when the build runs in a standard forked daemon"() {
        given:
        executer.usingExecutable(gradleUnderTty.name)
        buildFile << checkTtyTask()

        when:
        succeeds("checkTty")

        then:
        outputContains("tty available: false")
    }

    def "build logic can access the tty when the build runs in the client process"() {
        given:
        executer.withArgument("--no-daemon")
        executer.useOnlyRequestedJvmOpts()
        executer.withCommandLineGradleOpts(DaemonParameters.DEFAULT_JVM_ARGS)
        propertiesFile.writeProperties('org.gradle.jvmargs': DaemonParameters.DEFAULT_JVM_ARGS.join(" ") + " -ea")
        executer.usingExecutable(gradleUnderTty.name)
        buildFile << checkTtyTask()

        when:
        succeeds("checkTty")

        then:
        outputContains("tty available: true")
        outputDoesNotContain("To honour the JVM settings for this build a single-use Daemon process will be forked.")
    }

    def "build logic can access the tty of a foreground daemon"() {
        given:
        executer.useOnlyRequestedJvmOpts()
        executer.withCommandLineGradleOpts(DaemonParameters.DEFAULT_JVM_ARGS)
        executer.usingExecutable(gradleUnderTty.name)
        def foreground = startAForegroundDaemon()

        and:
        // Run a build against the foreground daemon. The client has no terminal.
        // The build runs in the foreground daemon process, which does.
        executer.useOnlyRequestedJvmOpts()
        propertiesFile.writeProperties('org.gradle.jvmargs': DaemonParameters.DEFAULT_JVM_ARGS.join(" ") + " -ea")
        buildFile << checkTtyTask()

        when:
        succeeds("checkTty")

        then:
        outputContains("tty available: true")
        outputDoesNotContain(DaemonStartupMessage.STARTING_DAEMON_MESSAGE)

        cleanup:
        foreground?.abort()
    }

    def "build logic can access the tty when the build runs through TestKit with debug enabled"() {
        given:
        def registry = new DefaultModuleRegistry(new GradleInstallation(distribution.gradleHomeDir))
        def testKitClasspath = registry.getRuntimeClasspath("gradle-test-kit").asFiles
        def clientSource = file("TestKitTtyClient.java") << """
            import org.gradle.testkit.runner.BuildResult;
            import org.gradle.testkit.runner.GradleRunner;
            import java.io.File;

            public class TestKitTtyClient {
                public static void main(String[] args) {
                    try {
                        File gradleInstallation = new File(args[0]);
                        File projectDir = new File(args[1]);
                        File testKitDir = new File(args[2]);
                        BuildResult result = GradleRunner.create()
                            .withGradleInstallation(gradleInstallation)
                            .withProjectDir(projectDir)
                            .withTestKitDir(testKitDir)
                            .withArguments("checkTty")
                            .withDebug(true)
                            .forwardOutput()
                            .build();

                        System.out.println("client build finished: " + result.task(":checkTty").getOutcome());

                        // Exit explicitly since the TestKit API keeps non-daemon threads running
                        // and cleans them up in a JVM shutdown hook.
                        System.exit(0);
                    } catch (Throwable t) {
                        t.printStackTrace();
                        System.exit(1);
                    }
                }
            }
        """

        and:
        settingsFile << "rootProject.name = 'testkit-project'"
        buildFile << checkTtyTask()

        when:
        // Compile TestKitTtyClient using java source-file mode and execute
        // under a pseudo tty.
        def output = runProcessUnderTty([
            Jvm.current().javaExecutable.absolutePath,
            "-cp",
            testKitClasspath.join(File.pathSeparator),
            clientSource.absolutePath,
            distribution.gradleHomeDir.absolutePath,
            testDirectory.absolutePath,
            file("test-kit-dir").absolutePath
        ])

        then:
        output.contains("tty available: true")
        output.contains("client build finished: SUCCESS")
    }

    private static String checkTtyTask() {
        """
            tasks.register("checkTty") {
                doLast {
                    def console = System.console()
                    // Java 22+ returns a Console even when no terminal is attached
                    def tty = console != null && (!console.respondsTo("isTerminal") || console.isTerminal())
                    println("tty available: " + tty)
                }
            }
        """
    }

    /**
     * Runs the given command under a pseudo-terminal and returns its combined output.
     */
    private String runProcessUnderTty(List<String> command) {
        def builder = new ProcessBuilder([runUnderTty.absolutePath] + command)
        builder.directory(testDirectory)
        builder.redirectErrorStream(true)
        def process = builder.start()
        // Close the child's stdin so that the pseudo-terminal wrapper sees end of input and
        // exits when the command completes
        process.outputStream.close()
        def output = new StringBuilder()
        def outputReader = process.consumeProcessOutputStream(output)
        try {
            assert process.waitFor(120, TimeUnit.SECONDS)
        } finally {
            process.destroyForcibly()
            outputReader.join(10_000)
            println output
        }
        return output.toString()
    }

    /**
     * Writes wrapper scripts that run a command under a pseudo-terminal. On macOS, BSD script
     * accepts the command as an argument vector. On Linux, util-linux script only accepts a
     * command string, so the arguments are quoted into one. Arguments containing single quotes
     * are rejected rather than escaped.
     */
    private TestFile writeRunUnderTty() {
        def runUnderTty = file("run-under-tty") << '''#!/bin/sh
if [ "$(uname)" = "Darwin" ]; then
    exec /usr/bin/script -q /dev/null "$@"
else
    cmd=''
    for arg in "$@"; do
        case "$arg" in
            *"'"*) echo "unsupported single quote in argument: $arg" >&2; exit 1;;
        esac
        cmd="$cmd '$arg'"
    done
    exec /usr/bin/script -qec "$cmd" /dev/null
fi
'''
        runUnderTty.setExecutable(true)
        return runUnderTty
    }

    private TestFile writeGradleUnderTty(TestFile runUnderTty) {
        def gradleUnderTty = file("gradle-under-tty") << """#!/bin/sh
exec "${runUnderTty}" "${distribution.gradleHomeDir.file("bin/gradle")}" "\$@"
"""
        gradleUnderTty.setExecutable(true)
        return gradleUnderTty
    }

}
