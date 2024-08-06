/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.integtests.api.problems.internal

import org.gradle.api.problems.internal.ResolutionFailureData
import org.gradle.integtests.fixtures.GroovyBuildScriptLanguage
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.problems.ProblemEvent
import org.gradle.tooling.events.problems.SingleProblemEvent
import org.gradle.tooling.events.problems.internal.GeneralData

/**
 * Tests that the tooling API can receive and process a problem containing additional {@link ResolutionFailureData}
 * data.
 */
@TargetGradleVersion(">=8.11")
@ToolingApiVersion(">=8.11")
class ResolutionFailureDataCrossVersionIntegrationTest extends ToolingApiSpecification {
    @ToolingApiVersion(">=8.11 <8.12")
    def "can supply ResolutionFailureData  (Tooling API client [8.11,8.12)"() {
        given:
        withReportProblemTask """
            TestResolutionFailure failure = new TestResolutionFailure()

            getProblems().getReporter().reporting {
                it.id("id", "shortProblemMessage")
                .additionalData(ResolutionFailureDataSpec.class, data -> data.from(failure))
            }
        """

        when:
        List<GeneralData> failureData = runAndGetProblems()
            .findAll { it instanceof SingleProblemEvent }
            .collect { ProblemEvent problem -> problem.additionalData as GeneralData }

        then:
        failureData.size() >= 1 // Depending on Java version, we might get a Java version test execution failure first, so just check the last one
        failureData.last().asMap.tap { Map d ->
            assert d.problemId == "UNKNOWN_RESOLUTION_FAILURE"
            assert d.requestTarget == "test failure"
            assert d.problemDisplayName == "Unknown resolution failure"
        }
    }

    @ToolingApiVersion(">=8.12")
    def "can supply ResolutionFailureData (Tooling API client >= 8.12)"() {
        given:
        withReportProblemTask """
            TestResolutionFailure failure = new TestResolutionFailure()

            getProblems().getReporter().reporting {
                it.id("id", "shortProblemMessage")
                .additionalData(ResolutionFailureDataSpec.class, data -> data.from(failure))
            }
        """

        when:
        List<GeneralData> failureData = runAndGetProblems().collect { ProblemEvent event ->
            event.problem.additionalData as GeneralData
        }

        then:
        failureData.size() >= 1 // Depending on Java version, we might get a Java version test execution failure first, so just check the last one
        failureData.last().asMap.tap { Map d ->
            assert d.problemId == "UNKNOWN_RESOLUTION_FAILURE"
            assert d.requestTarget == "test failure"
            assert d.problemDisplayName == "Unknown resolution failure"
        }
    }

    List<ProblemEvent> runAndGetProblems() {
        def listener = new ProblemProgressListener()
        withConnection { connection ->
            connection.newBuild().forTasks('reportProblem')
                .addProgressListener(listener)
                .run()
        }
        return listener.problems
    }

    def withReportProblemTask(@GroovyBuildScriptLanguage String taskActionMethodBody) {
        buildFile """
            import org.gradle.api.internal.catalog.problems.ResolutionFailureProblemId
            import org.gradle.api.problems.Severity
            import org.gradle.api.problems.internal.ResolutionFailureDataSpec
            import org.gradle.internal.component.resolution.failure.interfaces.ResolutionFailure

            class TestResolutionFailure implements ResolutionFailure {
                @Override
                public String describeRequestTarget() {
                    return "test failure";
                }

                @Override
                public ResolutionFailureProblemId getProblemId() {
                    return ResolutionFailureProblemId.UNKNOWN_RESOLUTION_FAILURE;
                }
            }

            abstract class ProblemReportingTask extends DefaultTask {
                @Inject
                protected abstract Problems getProblems();

                @TaskAction
                void run() {
                    $taskActionMethodBody
                }
            }

            tasks.register("reportProblem", ProblemReportingTask)
        """
    }

    private static class ProblemProgressListener implements ProgressListener {
        List<ProblemEvent> problems = []

        @Override
        void statusChanged(ProgressEvent event) {
            if (event instanceof SingleProblemEvent) {
                this.problems.add(event)
            }
        }
    }
}
