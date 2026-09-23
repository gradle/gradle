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

package org.gradle.launcher

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import org.gradle.util.GradleVersion
import org.junit.Rule

import java.util.concurrent.TimeUnit

@Requires(value = TestExecutionPreconditions.NotEmbeddedExecutor, reason = "output is redirected by the command-line client")
class AgentModeIntegrationTest extends AbstractIntegrationSpec {

    private static final String BUILDS_DIR = ".gradle/agent/builds"

    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            tasks.register("hello") {
                doLast {
                    println("Hello from the task")
                    System.err.println("Error from the task")
                }
            }
            tasks.register("broken") {
                doLast {
                    throw new RuntimeException("task is broken")
                }
            }
        """
    }

    def "writes all output to a file and only prints its path"() {
        when:
        succeeds("hello", "--agent")

        then:
        def agentOutput = assertOnlyLogFilePathPrinted()
        errorOutput.trim().empty

        and:
        agentOutput.text.contains("> Task :hello")
        agentOutput.text.contains("Hello from the task")
        agentOutput.text.contains("Error from the task")
        agentOutput.text.contains("BUILD SUCCESSFUL")
    }

    def "prints only the file path at log level #logLevel"() {
        when:
        succeeds("hello", "--agent", logLevel)

        then:
        def agentOutput = assertOnlyLogFilePathPrinted()
        errorOutput.trim().empty
        agentOutput.text.contains("Hello from the task")

        where:
        logLevel << ["--quiet", "--warn", "--info", "--debug"]
    }

    def "writes build failure to the file"() {
        when:
        fails("broken", "--agent")

        then:
        def agentOutput = assertOnlyLogFilePathPrinted()
        errorOutput.trim().empty

        and:
        agentOutput.text.contains("task is broken")
        agentOutput.text.contains("BUILD FAILED")
    }

    def "prints the file location and streams output to the file while the build is still running"() {
        given:
        server.start()
        settingsFile << """
            print("Message from settings")
            print(" continued")
            ${server.callFromBuild("settings")}
        """

        when:
        def settings = server.expectAndBlock("settings")
        def build = executer.withTasks("hello").withArgument("--agent").start()
        settings.waitForAllPendingCalls()

        then:
        def agentOutput = assertOnlyLogFilePathPrinted(build.standardOutput)
        ConcurrentTestUtil.poll {
            assert agentOutput.text.contains("Message from settings continued")
        }
        !agentOutput.text.contains("BUILD SUCCESSFUL")

        when:
        settings.releaseAll()
        build.waitForFinish()

        then:
        agentOutput.text.contains("BUILD SUCCESSFUL")
    }

    def "only writes complete lines to the file"() {
        given:
        server.start()
        settingsFile << """
            println("Complete line from settings")
            print("Partial line from settings")
            new URL("${server.uri("settings")}").text
        """

        when:
        def settings = server.expectAndBlock("settings")
        def build = executer.withTasks("hello").withArgument("--agent").start()
        settings.waitForAllPendingCalls()

        then:
        def agentOutput = assertOnlyLogFilePathPrinted(build.standardOutput)
        ConcurrentTestUtil.poll {
            assert agentOutput.text.contains("Complete line from settings\n")
        }
        sleep(1000) // give the partial line a chance to reach the client
        agentOutput.text.endsWith("\n")
        !agentOutput.text.contains("Partial line from settings")

        when:
        settings.releaseAll()
        build.waitForFinish()

        then:
        agentOutput.text.contains("Partial line from settings")
    }

    def "writes an invalid option to the file"() {
        when:
        executer.withWarningMode(null)
        fails("hello", "--agent", "--warning-mode=bogus")

        then:
        def agentOutput = assertOnlyLogFilePathPrinted()
        errorOutput.trim().empty
        agentOutput.text.contains("Argument value 'bogus' given for --warning-mode option is invalid")
    }

    def "prints the file location before starting the daemon"() {
        given:
        executer.requireDaemon().requireIsolatedDaemons().withBuildJvmOpts("-Xnot-a-jvm-option")

        when:
        fails("hello", "--agent")

        then:
        assertOnlyLogFilePathPrinted().text.contains("Unable to start the daemon process")
    }

    def "writes the file to the root directory when run from a subproject"() {
        given:
        settingsFile << "\ninclude('sub')"
        file("sub/build.gradle") << "tasks.register('inSub')"

        when:
        executer.inDirectory(file("sub"))
        succeeds("inSub", "--agent")

        then:
        assertOnlyLogFilePathPrinted()
        !file("sub/.gradle/agent").exists()
    }

    def "can be enabled with a Gradle property and disabled on the command line"() {
        given:
        file("gradle.properties") << "org.gradle.agent=true"

        when:
        succeeds("hello")

        then:
        assertOnlyLogFilePathPrinted()

        when:
        succeeds("hello", "--no-agent")

        then:
        outputContains("Hello from the task")
        file(BUILDS_DIR).listFiles().size() == 1
    }

    def "environment variable takes precedence over properties and command line flag over environment variable"() {
        given:
        file("gradle.properties") << "org.gradle.agent=false"

        when:
        executer.withEnvironmentVars(ORG_GRADLE_AGENT: "true")
        succeeds("hello")

        then:
        assertOnlyLogFilePathPrinted()

        when:
        executer.withEnvironmentVars(ORG_GRADLE_AGENT: "true")
        succeeds("hello", "--no-agent")

        then:
        outputContains("Hello from the task")
        file(BUILDS_DIR).listFiles().size() == 1
    }

    def "agent mode is #expected with gradle.properties #gradleProperty, GRADLE_OPTS #gradleOpts, environment variable #envVar and args #args"() {
        given:
        if (gradleProperty != null) {
            file("gradle.properties") << "org.gradle.agent=$gradleProperty"
        }
        if (gradleOpts != null) {
            executer.withCommandLineGradleOpts("-Dorg.gradle.agent=$gradleOpts")
        }
        if (envVar != null) {
            executer.withEnvironmentVars(ORG_GRADLE_AGENT: envVar)
        }

        when:
        succeeds(["hello"] + args)

        then:
        file(BUILDS_DIR).exists() == expected
        expected ? assertOnlyLogFilePathPrinted() : output.contains("Hello from the task")

        where:
        gradleProperty | gradleOpts | envVar  | args           | expected
        "true"         | null       | null    | []             | true
        "true"         | "false"    | null    | []             | false
        "false"        | "true"     | null    | []             | true
        "false"        | "true"     | "false" | []             | false
        "true"         | "false"    | "true"  | []             | true
        "true"         | "true"     | "true"  | ["--no-agent"] | false
        "false"        | "false"    | "false" | ["--agent"]    | true
    }

    def "writes #option output to the file"() {
        when:
        succeeds(option, "--agent")

        then:
        assertOnlyLogFilePathPrinted().text.contains(expectedOutput)

        where:
        option      | expectedOutput
        "--help"    | "USAGE: gradle [option...] [task...]"
        "--version" | "Gradle ${GradleVersion.current().version}"
    }

    def "environment variable value #value is treated as #expected"() {
        given:
        file("gradle.properties") << "org.gradle.agent=${!expected}"

        when:
        executer.withEnvironmentVars(ORG_GRADLE_AGENT: value)
        succeeds("hello")

        then:
        file(BUILDS_DIR).exists() == expected

        where:
        value   | expected
        "true"  | true
        "TRUE"  | true
        "false" | false
        "1"     | false
        ""      | false
    }

    def "writes the file to the requested project cache directory"() {
        when:
        succeeds("hello", "--agent", "--project-cache-dir", "custom-cache")

        then:
        assertOnlyLogFilePathPrinted(output, "custom-cache/agent/builds").text.contains("Hello from the task")
        !file(BUILDS_DIR).exists()
    }

    def "writes the output of each build to a separate file"() {
        when:
        succeeds("hello", "--agent")
        def first = assertOnlyLogFilePathPrinted()
        fails("broken", "--agent")
        def second = assertOnlyLogFilePathPrinted()

        then:
        first != second
        first.text.contains("Hello from the task")
        !first.text.contains("task is broken")
        second.text.contains("task is broken")
        !second.text.contains("Hello from the task")
    }

    def "build #description cleans up build output that is older than 7 days"() {
        given:
        def oldOutput = createAgentOutput("old", 8)
        def recentOutput = createAgentOutput("recent", 6)

        when:
        succeeds(["hello"] + args)

        then:
        ConcurrentTestUtil.poll {
            oldOutput.assertDoesNotExist()
        }
        recentOutput.assertExists()

        where:
        description          | args
        "in agent mode"     | ["--agent"]
        "not in agent mode" | []
    }

    private TestFile createAgentOutput(String invocation, int daysAgo) {
        def lastModified = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(daysAgo)
        def dir = file("$BUILDS_DIR/$invocation").createDir()
        dir.file("build-output.log").createFile().lastModified = lastModified
        dir.lastModified = lastModified
        return dir
    }

    /**
     * Asserts that the given standard output is nothing but the location of the output file, and returns that file.
     */
    private TestFile assertOnlyLogFilePathPrinted(String standardOutput = output, String buildsDir = BUILDS_DIR) {
        def lines = standardOutput.readLines()
        assert lines.size() == 1
        def outputFile = new TestFile(lines[0])
        assert outputFile.name == "build-output.log"
        assert outputFile.parentFile.name ==~ /[a-z2-7]{26}/
        assert outputFile.parentFile.parentFile == file(buildsDir)
        return outputFile
    }
}
