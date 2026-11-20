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

package org.gradle.integtests.tooling.r9100

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildException
import org.gradle.tooling.Failure
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.problems.Problem
import org.gradle.tooling.events.problems.SingleProblemEvent

@ToolingApiVersion(">=8.14")
@TargetGradleVersion(">=9.10")
class ProjectNotFoundProblemCrossVersionSpec extends ToolingApiSpecification {

    interface ProjectNotFoundView {
        String getRequestedPath()

        List<String> getCandidates()

        String getBuildPath()
    }

    def "client receives close project paths of an unknown project with the build failure"() {
        given:
        file("app").mkdirs()
        file("lib").mkdirs()
        settingsFile << "include 'app', 'lib'"
        buildFile << "project(':ap')"

        when:
        def listener = new ProblemProgressListener()
        withConnection { connection ->
            connection.newBuild()
                .forTasks("help")
                .addProgressListener(listener)
                .withDetailedFailure()
                .run()
        }

        then:
        def e = thrown(BuildException)
        def problem = problemsOf(e.failures).find { it.definition.id.name == "Project not found" }
        problem != null
        problem.definition.id.group.name == "Build Definition"
        def data = problem.additionalData.get(ProjectNotFoundView)
        data.requestedPath == ":ap"
        data.candidates == [":app"]
        data.buildPath == ":"

        and: "the problem is only attached to the failure"
        listener.problems.every { it.definition.id.name != "Project not found" }
    }

    def "client receives no problem for an unknown project lookup that is recovered from"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            try {
                project(':nope')
            } catch (UnknownProjectException e) {
                println("recovered")
            }
        """

        when:
        def listener = new ProblemProgressListener()
        withConnection { connection ->
            connection.newBuild()
                .forTasks("help")
                .addProgressListener(listener)
                .run()
        }

        then:
        listener.problems.every { it.definition.id.name != "Project not found" }
    }

    private static List<Problem> problemsOf(List<? extends Failure> failures) {
        failures.collectMany { it.problems + problemsOf(it.causes) }
    }

    static class ProblemProgressListener implements ProgressListener {

        List<Problem> problems = []

        @Override
        void statusChanged(ProgressEvent event) {
            if (event instanceof SingleProblemEvent) {
                problems.add((event as SingleProblemEvent).problem)
            }
        }
    }
}
