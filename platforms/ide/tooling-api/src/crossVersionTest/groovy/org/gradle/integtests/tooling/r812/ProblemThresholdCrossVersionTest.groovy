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
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.problems.ProblemSummariesEvent
import org.gradle.tooling.events.problems.SingleProblemEvent

import static org.gradle.api.problems.ReportingScript.getProblemReportingScript
import static org.gradle.integtests.tooling.r86.ProblemsServiceModelBuilderCrossVersionTest.getBuildScriptSampleContent
import static org.gradle.integtests.tooling.r89.ProblemProgressEventCrossVersionTest.failureMessage
import static org.gradle.problems.internal.services.DefaultProblemSummarizer.THRESHOLD_DEFAULT_VALUE
import static org.gradle.problems.internal.services.DefaultProblemSummarizer.THRESHOLD_OPTION

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
        validateProblemsRange(0..(THRESHOLD_DEFAULT_VALUE - 1), problems)
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
        validateProblemsRange(0..(totalSentEventsCount - 1), problems)
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
        validateProblemsRange(0..0, problems)
        failureMessage(problems[0].problem.failure) == 'test'
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
        validateProblemsRange(0..(THRESHOLD_DEFAULT_VALUE - 1), problems)
        def problemSummariesEvent = listener.summariesEvent as ProblemSummariesEvent
        def summaries = problemSummariesEvent.problemSummaries
        summaries.size() == 1
    }

    def "Two problem ids exceed threshold"() {
        given:
        def exceedingCount = 2
        def differentProblemCount = 4
        def threshold = THRESHOLD_DEFAULT_VALUE + exceedingCount
        buildFile getProblemReportingScript("""
            ${getProblemReportingBody(threshold)}
            ${getProblemReportingBody(threshold, "testCategory2", "label2")}
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
        problems.size() == THRESHOLD_DEFAULT_VALUE * 2
        validateProblemsRange(0..(THRESHOLD_DEFAULT_VALUE - 1), problems)
        validateProblemsRange(THRESHOLD_DEFAULT_VALUE..(problems.size() - 1), problems, "label2", "Generic")
        def problemSummariesEvent = listener.summariesEvent as ProblemSummariesEvent
        def summaries = problemSummariesEvent.problemSummaries
        summaries.size() == 2
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
        validateProblemsRange(0..(thresholdInOption - 1), problems)
        def problemSummariesEvent = listener.summariesEvent as ProblemSummariesEvent
        def summaries = problemSummariesEvent.problemSummaries
        summaries.size() == 1
    }

    boolean validateProblemsRange(IntRange range, Collection<SingleProblemEvent> problems, String id = "label", String group = "Generic") {
        range.every { int index ->
            problems[index].problem.definition.id.displayName == id &&
                problems[index].problem.definition.id.group.displayName == group
        }
    }

    static class ProblemProgressListener implements ProgressListener {
        List<SingleProblemEvent> problems = []
        ProblemSummariesEvent summariesEvent = null


        @Override
        void statusChanged(ProgressEvent event) {
            if (event instanceof SingleProblemEvent) {
                def singleProblem = event as SingleProblemEvent

                // Ignore problems caused by the minimum JVM version deprecation.
                // These are emitted intermittently depending on the version of Java used to run the test.
                if (singleProblem.problem.definition.id.name == "executing-gradle-on-jvm-versions-and-lower") {
                    return
                }

                this.problems.add(event)
            } else if (event instanceof ProblemSummariesEvent) {
                assert summariesEvent == null, "already received a ProblemsSummariesEvent, there should only be one"
                summariesEvent = event
            }
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
