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
import org.junit.Rule

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
        def agentOutput = agentOutputFile()
        errorOutput.trim().empty

        and:
        agentOutput.text.contains("> Task :hello")
        agentOutput.text.contains("Hello from the task")
        agentOutput.text.contains("Error from the task")
        agentOutput.text.contains("BUILD SUCCESSFUL")
    }

    def "writes build failure to the file"() {
        when:
        fails("broken", "--agent")

        then:
        def agentOutput = agentOutputFile()
        errorOutput.trim().empty

        and:
        agentOutput.text.contains("task is broken")
        agentOutput.text.contains("BUILD FAILED")
    }

    def "prints the file location and streams output to the file while the build is still running"() {
        given:
        server.start()
        settingsFile << """
            println("Message from settings")
            ${server.callFromBuild("settings")}
        """

        when:
        def settings = server.expectAndBlock("settings")
        def build = executer.withTasks("hello").withArgument("--agent").start()
        settings.waitForAllPendingCalls()

        then:
        ConcurrentTestUtil.poll {
            assert agentOutputFile(build.standardOutput).text.contains("Message from settings")
        }
        def agentOutput = agentOutputFile(build.standardOutput)
        !agentOutput.text.contains("BUILD SUCCESSFUL")

        when:
        settings.releaseAll()
        build.waitForFinish()

        then:
        agentOutput.text.contains("BUILD SUCCESSFUL")
    }

    def "prints the file location before starting the daemon"() {
        given:
        executer.requireDaemon().requireIsolatedDaemons().withBuildJvmOpts("-Xnot-a-jvm-option")

        when:
        fails("hello", "--agent")

        then:
        agentOutputFile().text.contains("Unable to start the daemon process")
    }

    def "writes the file to the root directory when run from a subproject"() {
        given:
        settingsFile << "\ninclude('sub')"
        file("sub/build.gradle") << "tasks.register('inSub')"

        when:
        executer.inDirectory(file("sub"))
        succeeds("inSub", "--agent")

        then:
        agentOutputFile()
        !file("sub/.gradle/agent").exists()
    }

    def "can be enabled with a Gradle property and disabled on the command line"() {
        given:
        file("gradle.properties") << "org.gradle.agent=true"

        when:
        succeeds("hello")

        then:
        agentOutputFile()

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
        agentOutputFile()

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
        expected ? agentOutputFile() : output.contains("Hello from the task")

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

    def "explicit console option disables agent mode requested via #description with a warning"() {
        given:
        if (property) {
            file("gradle.properties") << "org.gradle.agent=true"
        }

        when:
        executer.withEnvironmentVars(envVars)
        succeeds(["hello", "--console=plain"] + args)

        then:
        outputContains("Agent mode has been disabled because the --console option was specified.")
        outputContains("Hello from the task")
        !file(BUILDS_DIR).exists()

        where:
        description            | args        | envVars                    | property
        "command line flag"    | ["--agent"] | [:]                        | false
        "environment variable" | []          | [ORG_GRADLE_AGENT: "true"] | false
        "Gradle property"      | []          | [:]                        | true
    }

    def "does not warn about the console option when agent mode is not requested"() {
        when:
        succeeds("hello", "--console=plain")

        then:
        outputDoesNotContain("Agent mode")
    }

    def "writes newlines to standard error while the build is running"() {
        given:
        buildFile << """
            tasks.register("slow") {
                doLast {
                    Thread.sleep(2000)
                }
            }
        """

        when:
        executer.withCommandLineGradleOpts("-Dorg.gradle.internal.testing.agent.heartbeat.millis=100")
        succeeds("slow", "--agent")

        then:
        agentOutputFile()
        result.error ==~ /\n{5,}/
    }

    def "does not write a heartbeat when agent mode is not enabled"() {
        given:
        buildFile << """
            tasks.register("slow") {
                doLast {
                    Thread.sleep(1000)
                }
            }
        """

        when:
        executer.withCommandLineGradleOpts("-Dorg.gradle.internal.testing.agent.heartbeat.millis=100")
        succeeds("slow")

        then:
        result.error.empty
    }

    def "writes the file to the requested project cache directory"() {
        when:
        succeeds("hello", "--agent", "--project-cache-dir", "custom-cache")

        then:
        agentOutputFile(output, "custom-cache/agent/builds").text.contains("Hello from the task")
        !file(BUILDS_DIR).exists()
    }

    def "writes the output of each build to a separate file"() {
        when:
        succeeds("hello", "--agent")
        def first = agentOutputFile()
        fails("broken", "--agent")
        def second = agentOutputFile()

        then:
        first != second
        first.text.contains("Hello from the task")
        !first.text.contains("task is broken")
        second.text.contains("task is broken")
        !second.text.contains("Hello from the task")
    }

    /**
     * Verifies that the given standard output is nothing but the location of the output file, and returns that file.
     */
    private TestFile agentOutputFile(String standardOutput = output, String buildsDir = BUILDS_DIR) {
        def lines = standardOutput.readLines().findAll { !it.empty }
        assert lines.size() == 1
        def outputFile = new TestFile(lines[0])
        assert outputFile.name == "build-output.log"
        assert outputFile.parentFile.name ==~ /[a-z2-7]{26}/
        assert outputFile.parentFile.parentFile == file(buildsDir)
        return outputFile
    }
}
