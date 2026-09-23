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

package org.gradle.integtests.tooling.r990

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.problems.Problem
import org.gradle.tooling.events.problems.SingleProblemEvent

@ToolingApiVersion(">=9.9")
@TargetGradleVersion(">=9.9")
class AgentModeCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        buildFile """
            tasks.register("hello") {
                doLast {
                    println("Hello from the task")
                }
            }
        """
    }

    def "agent mode requested via #description has no effect and is reported as a problem"() {
        given:
        if (property) {
            file("gradle.properties") << "org.gradle.agent=true"
        }
        def listener = new ProblemProgressListener()
        def output = new ByteArrayOutputStream()

        when:
        withConnection { connection ->
            connection.newBuild()
                .forTasks("hello")
                .withArguments(arguments)
                .setEnvironmentVariables(environment)
                .setStandardOutput(output)
                .addProgressListener(listener)
                .run()
        }

        then:
        output.toString().contains("Hello from the task")
        !file(".gradle/agent").exists()

        and:
        listener.problems.size() == 1
        verifyAll(listener.problems[0]) {
            definition.id.name == "ignored-for-tooling-api"
            definition.id.group.name == "agent-mode"
            contextualLabel.contextualLabel == "Agent mode has been ignored because the build was run through the Tooling API."
        }

        where:
        description            | arguments   | environment                | property
        "build argument"       | ["--agent"] | [:]                        | false
        "environment variable" | []          | [ORG_GRADLE_AGENT: "true"] | false
        "Gradle property"      | []          | [:]                        | true
    }

    def "agent mode does not imply other options for a Tooling API build"() {
        given:
        buildFile """
            tasks.register("broken") {
                doLast {
                    throw new RuntimeException("task is broken")
                }
            }
        """
        def error = new ByteArrayOutputStream()

        when:
        withConnection { connection ->
            connection.newBuild()
                .forTasks("broken")
                .withArguments("--agent")
                .setStandardError(error)
                .run()
        }

        then:
        thrown(Exception)
        // Agent mode would imply --stacktrace, which prints the exception instead of this hint
        error.toString().contains("Run with --stacktrace option to get the stack trace.")
        !error.toString().contains("* Exception is:")
    }

    def "no problem is reported when agent mode is not requested"() {
        given:
        def listener = new ProblemProgressListener()

        when:
        withConnection { connection ->
            connection.newBuild()
                .forTasks("hello")
                .withArguments("--no-agent")
                .addProgressListener(listener)
                .run()
        }

        then:
        listener.problems.empty
    }

    static class ProblemProgressListener implements ProgressListener {

        List<Problem> problems = []

        @Override
        void statusChanged(ProgressEvent event) {
            if (event instanceof SingleProblemEvent) {
                def singleProblem = event as SingleProblemEvent

                // Ignore problems caused by the minimum JVM version deprecation.
                // These are emitted intermittently depending on the version of Java used to run the test.
                if (singleProblem.problem.definition.id.name == "executing-gradle-on-jvm-versions-and-lower") {
                    return
                }

                this.problems.add(singleProblem.problem)
            }
        }
    }
}
