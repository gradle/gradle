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
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions

@Requires(value = TestExecutionPreconditions.NotEmbeddedExecutor, reason = "output is redirected by the command-line client")
class AgentModeIntegrationTest extends AbstractIntegrationSpec {

    private static final String OUTPUT_FILE = ".gradle/agent/builds/latest/build-output.log"

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
        def agentOutput = file(OUTPUT_FILE)
        output.trim() == agentOutput.absolutePath
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
        def agentOutput = file(OUTPUT_FILE)
        output.trim() == agentOutput.absolutePath
        errorOutput.trim().empty

        and:
        agentOutput.text.contains("task is broken")
        agentOutput.text.contains("BUILD FAILED")
    }

    def "writes the file to the root directory when run from a subproject"() {
        given:
        settingsFile << "\ninclude('sub')"
        file("sub/build.gradle") << "tasks.register('inSub')"

        when:
        executer.inDirectory(file("sub"))
        succeeds("inSub", "--agent")

        then:
        output.trim() == file(OUTPUT_FILE).absolutePath
        !file("sub/.gradle/agent").exists()
    }

    def "can be enabled with a Gradle property and disabled on the command line"() {
        given:
        file("gradle.properties") << "org.gradle.agent=true"

        when:
        succeeds("hello")

        then:
        output.trim() == file(OUTPUT_FILE).absolutePath

        when:
        file(OUTPUT_FILE).delete()
        succeeds("hello", "--no-agent")

        then:
        outputContains("Hello from the task")
        !file(OUTPUT_FILE).exists()
    }

    def "writes the file to the requested project cache directory"() {
        when:
        succeeds("hello", "--agent", "--project-cache-dir", "custom-cache")

        then:
        def agentOutput = file("custom-cache/agent/builds/latest/build-output.log")
        output.trim() == agentOutput.absolutePath
        agentOutput.text.contains("Hello from the task")
        !file(".gradle/agent").exists()
    }

    def "replaces the output of the previous build"() {
        when:
        succeeds("hello", "--agent")
        fails("broken", "--agent")

        then:
        def text = file(OUTPUT_FILE).text
        text.contains("task is broken")
        !text.contains("Hello from the task")
    }
}
