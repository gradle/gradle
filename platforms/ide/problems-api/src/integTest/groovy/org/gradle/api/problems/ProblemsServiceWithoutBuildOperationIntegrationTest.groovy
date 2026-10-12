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

package org.gradle.api.problems

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class ProblemsServiceWithoutBuildOperationIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        enableProblemsApiCheck()
    }

    @Issue("https://github.com/gradle/gradle/issues/35885")
    def "problem reported from a thread without a current build operation is not lost"() {
        given:
        settingsFile """
            import org.gradle.api.problems.ProblemGroup
            import org.gradle.api.problems.ProblemId
            import org.gradle.api.problems.Problems
            import org.gradle.api.services.BuildService
            import org.gradle.api.services.BuildServiceParameters
            import org.gradle.build.event.BuildEventsListenerRegistry
            import org.gradle.tooling.events.FinishEvent
            import org.gradle.tooling.events.OperationCompletionListener
            import javax.inject.Inject

            abstract class BuildObserver implements BuildService<BuildServiceParameters.None>, OperationCompletionListener {
                @Inject
                abstract Problems getProblems()

                @Override
                void onFinish(FinishEvent event) {
                    ProblemGroup problemGroup = ProblemGroup.create("issues", "issues")
                    ProblemId id = ProblemId.create("finished", "task finished", problemGroup)
                    problems.reporter.report(id) {}
                }
            }

            def observer = gradle.sharedServices.registerIfAbsent("buildObserver", BuildObserver) {}
            services.get(BuildEventsListenerRegistry).onTaskCompletion(observer)
        """
        buildFile """
            tasks.register("ok")
        """

        when:
        run("ok")

        then:
        verifyAll(receivedProblem) {
            definition.id.fqid == 'issues:finished'
            definition.id.displayName == 'task finished'
        }

        and: "the problem also lands in the problems report file"
        def report = testDirectory.file("build/reports/problems/problems-report.html")
        report.exists()
        report.text.contains("task finished")
    }
}
