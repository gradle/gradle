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

package org.gradle.integtests.tooling.r980

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.problems.Problem
import org.gradle.tooling.events.problems.SingleProblemEvent
import spock.lang.Issue

@ToolingApiVersion(">=9.8")
@TargetGradleVersion(">=9.8")
class ProblemReportingFromEventListenerCrossVersionSpec extends ToolingApiSpecification {

    @Issue("https://github.com/gradle/gradle/issues/35885")
    def "client receives problem reported from a build event listener thread"() {
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
        def listener = new ProblemProgressListener()
        withConnection { connection ->
            connection.newBuild()
                .forTasks("ok")
                .addProgressListener(listener)
                .run()
        }

        then:
        listener.problems.size() == 1
        verifyAll(listener.problems[0]) {
            definition.id.name == 'finished'
            definition.id.displayName == 'task finished'
            definition.id.group.name == 'issues'
            definition.id.group.displayName == 'issues'
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
