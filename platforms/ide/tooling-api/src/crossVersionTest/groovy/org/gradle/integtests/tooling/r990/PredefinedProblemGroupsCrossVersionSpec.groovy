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
import spock.lang.Issue

@ToolingApiVersion(">=9.8")
@TargetGradleVersion(">=9.9")
class PredefinedProblemGroupsCrossVersionSpec extends ToolingApiSpecification {

    @Issue("https://github.com/gradle/gradle/issues/38670")
    def "client receives problems reported into predefined groups with their full group chain"() {
        given:
        buildFile """
            import org.gradle.api.problems.Problems
            import javax.inject.Inject

            abstract class ProblemReportingTask extends DefaultTask {
                @Inject
                abstract Problems getProblems()

                @TaskAction
                void run() {
                    problems.reporter.report(problems.groups.compilation.java.problem("Unused import")) {}
                    problems.reporter.report(problems.groups.transformation.group("KMP").group("JavaScript").problem("Bundle failed")) {}
                    problems.reporter.report(problems.groups.others.undefined.problem("Something odd")) {}
                }
            }

            tasks.register("reportProblem", ProblemReportingTask)
        """

        when:
        def listener = new ProblemProgressListener()
        withConnection { connection ->
            connection.newBuild()
                .forTasks("reportProblem")
                .addProgressListener(listener)
                .run()
        }

        then:
        listener.problems.size() == 3
        verifyAll(listener.problems[0].definition.id) {
            name == "Unused import"
            displayName == "Unused import"
            group.name == "Java"
            group.displayName == "Java"
            group.parent.name == "Compilation"
            group.parent.displayName == "Compilation"
            group.parent.parent == null
        }
        verifyAll(listener.problems[1].definition.id) {
            name == "Bundle failed"
            group.name == "JavaScript"
            group.parent.name == "KMP"
            group.parent.parent.name == "Transformation"
            group.parent.parent.parent == null
        }
        verifyAll(listener.problems[2].definition.id) {
            name == "Something odd"
            group.name == "Undefined"
            group.parent.name == "Others"
            group.parent.parent == null
        }
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
