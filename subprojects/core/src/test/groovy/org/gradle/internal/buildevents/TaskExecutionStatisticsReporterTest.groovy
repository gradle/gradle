/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.buildevents

import org.gradle.api.internal.tasks.execution.statistics.TaskExecutionStatistics
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.gradle.internal.logging.text.TestStyledTextOutputFactory
import org.gradle.util.TextUtil
import spock.lang.Specification
import spock.lang.Subject

@Subject(TaskExecutionStatisticsReporter)
class TaskExecutionStatisticsReporterTest extends Specification {
    private StyledTextOutputFactory textOutputFactory = new TestStyledTextOutputFactory()
    private TaskExecutionStatisticsReporter reporter = new TaskExecutionStatisticsReporter(textOutputFactory)

    def "does not report statistics given 0 tasks"() {
        when:
        reporter.buildFinished(new TaskExecutionStatistics(0, 0))

        then:
        (textOutputFactory as String) == ""
        0 * _
    }

    def "disallows negative task counts as input"() {
        when:
        reporter.buildFinished(new TaskExecutionStatistics(-1, 12))

        then:
        thrown IllegalArgumentException
    }

    def "properly pluralizes output"() {
        when:
        reporter.buildFinished(new TaskExecutionStatistics(1, 0))

        then:
        TextUtil.normaliseLineSeparators(textOutputFactory as String) == "{org.gradle.internal.buildevents.BuildResultLogger}{LIFECYCLE}1 actionable task: 1 executed, 0 avoided (0%)\n"
    }

    def "reports statistics with rounded percentages"() {
        when:
        reporter.buildFinished(new TaskExecutionStatistics(2, 1))

        then:
        TextUtil.normaliseLineSeparators(textOutputFactory as String) == "{org.gradle.internal.buildevents.BuildResultLogger}{LIFECYCLE}3 actionable tasks: 2 executed, 1 avoided (33%)\n"
    }
}
