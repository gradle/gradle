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

package org.gradle.integtests.tooling.r812

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.r85.CustomModel
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.problems.ProblemSummariesEvent
import org.gradle.tooling.events.problems.SingleProblemEvent

import static org.gradle.api.problems.ReportingScript.getProblemReportingScript
import static org.gradle.api.problems.internal.DefaultProblemSummarizer.THRESHOLD_DEFAULT_VALUE
import static org.gradle.api.problems.internal.DefaultProblemSummarizer.THRESHOLD_OPTION
import static org.gradle.integtests.tooling.r86.ProblemsServiceModelBuilderCrossVersionTest.getBuildScriptSampleContent
import static org.gradle.integtests.tooling.r89.ProblemProgressEventCrossVersionTest.ProblemProgressListener
import static org.gradle.integtests.tooling.r89.ProblemProgressEventCrossVersionTest.failureMessage

@ToolingApiVersion(">=8.12")
@TargetGradleVersion(">=8.12")
class ProblemThresholdCrossVersionTest extends ToolingApiSpecification {

    def "The summary shows the amount of additional skipped events"() {
        given:
        def exceedingCount = 2
        buildFile getProblemReportingScript("${getProblemReportingBody(THRESHOLD_DEFAULT_VALUE + exceedingCount)}")
        def listener = new ProblemProgressListener()

        when:
        withConnection {
            it.newBuild()
                .forTasks("reportProblem")
                .addProgressListener(listener)
                .run()
        }

        then:
        def problems = listener.problems
        problems.size() == THRESHOLD_DEFAULT_VALUE
        validateFirstNProblems(THRESHOLD_DEFAULT_VALUE, problems)
        def problemSummariesEvent = listener.summariesEvent as ProblemSummariesEvent
        problemSummariesEvent != null

        def summaries = problemSummariesEvent.problemSummaries
        summaries.size() == 1
        summaries.get(0).count == exceedingCount
    }

    def "No summaries if no events exceeded the threshold"() {
        def totalSentEventsCount = THRESHOLD_DEFAULT_VALUE + exceedingCount
        given:
        buildFile getProblemReportingScript("${getProblemReportingBody(totalSentEventsCount)}")
        def listener = new ProblemProgressListener()

        when:
        withConnection {
            it.newBuild()
                .forTasks("reportProblem")
                .addProgressListener(listener)
                .run()
        }

        then:
        def problems = listener.problems
        problems.size() == totalSentEventsCount
        validateFirstNProblems(totalSentEventsCount, problems)
        def problemSummariesEvent = listener.summariesEvent as ProblemSummariesEvent
        problemSummariesEvent != null
        def summaries = problemSummariesEvent.problemSummaries
        summaries.size() == 0

        where:
        exceedingCount << [-5, -1, 0]
    }

    @TargetGradleVersion(">=8.10.2 <8.11")
    def "No summaries received from Gradle versions before 8.12"() {
        given:
        def exceedingCount = 2
        buildFile getBuildScriptSampleContent(false, false, targetVersion, THRESHOLD_DEFAULT_VALUE + exceedingCount)
        def listener = new ProblemProgressListener()

        when:
        withConnection {
            it.model(CustomModel)
                .addProgressListener(listener)
                .get()
        }

        then:
        def problems = listener.problems
        problems.size() == 1 // 1 because older version does aggregation and only sends the first one.
        validateFirstNProblems(1, problems)
        failureMessage(problems[0].failure) == 'test'
        listener.summariesEvent == null
    }

    def "Events are still sent despite one group already ran into threshold"() {
        given:
        def exceedingCount = 2
        def differentProblemCount = 4
        def threshold = THRESHOLD_DEFAULT_VALUE + exceedingCount
        buildFile getProblemReportingScript("""
            ${getProblemReportingBody(threshold)}
            ${getProblemReportingBody(differentProblemCount, "testCategory2", "label2")}
            """)
        def listener = new ProblemProgressListener()

        when:
        withConnection {
            it.newBuild()
                .addProgressListener(listener)
                .forTasks("reportProblem")
                .run()
        }

        then:
        def problems = listener.problems
        problems.size() == THRESHOLD_DEFAULT_VALUE + differentProblemCount
        validateFirstNProblems(THRESHOLD_DEFAULT_VALUE, problems)
        def problemSummariesEvent = listener.summariesEvent as ProblemSummariesEvent
        def summaries = problemSummariesEvent.problemSummaries
        summaries.size() == 1
    }

    def "Problem summarization threshold can be set by an internal option"() {
        given:
        def exceedingCount = 2
        def thresholdInOption = 20
        def threshold = thresholdInOption + exceedingCount
        buildFile getProblemReportingScript("""
            ${getProblemReportingBody(threshold)}
            """)
        def listener = new ProblemProgressListener()

        when:
        withConnection {
            it.newBuild()
                .withSystemProperties([(THRESHOLD_OPTION.systemPropertyName): thresholdInOption.toString()])
                .addProgressListener(listener, OperationType.PROBLEMS)
                .forTasks("reportProblem")
                .run()
        }

        then:
        def problems = listener.problems
        problems.size() == thresholdInOption
        validateFirstNProblems(thresholdInOption, problems)
        def problemSummariesEvent = listener.summariesEvent as ProblemSummariesEvent
        def summaries = problemSummariesEvent.problemSummaries
        summaries.size() == 1
    }

    boolean validateFirstNProblems(int totalSentEventsCount, Collection<SingleProblemEvent> problems) {
        (0..totalSentEventsCount - 1).every { int index ->
            problems[index].definition.id.displayName == 'label' &&
                problems[index].definition.id.group.displayName == 'Generic'
        }
    }

    String getProblemReportingBody(int threshold, String category = "testcategory", String label = "label") {
        """($threshold).times {
                 problems.getReporter().reporting {
                    it.id("$category", "$label")
                      .details('Wrong API usage, will not show up anywhere')
                 }
             }
        """
    }
}
