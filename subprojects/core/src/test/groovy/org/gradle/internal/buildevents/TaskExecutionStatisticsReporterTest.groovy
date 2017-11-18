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
import org.gradle.internal.logging.text.TestStyledTextOutputFactory
import org.gradle.util.TextUtil
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

@Subject(TaskExecutionStatisticsReporter)
class TaskExecutionStatisticsReporterTest extends Specification {
    def textOutputFactory = new TestStyledTextOutputFactory()
    def reporter = new TaskExecutionStatisticsReporter(textOutputFactory)

    def "does not report statistics given 0 tasks"() {
        when:
        reporter.buildFinished(new TaskExecutionStatistics(0, 0, 0, 0))

        then:
        (textOutputFactory as String) == ""
        0 * _
    }

    def "disallows negative task counts as input"() {
        when:
        reporter.buildFinished(new TaskExecutionStatistics(-1, 12, 7, 0))

        then:
        thrown IllegalArgumentException
    }

    def "properly pluralizes output"() {
        when:
        reporter.buildFinished(new TaskExecutionStatistics(1, 0, 0, 0))

        then:
        TextUtil.normaliseLineSeparators(textOutputFactory as String) == "{org.gradle.internal.buildevents.BuildResultLogger}{LIFECYCLE}1 actionable task: 1 executed\n"
    }

    @Unroll
    def "reports only task counts > 0 (exec: #executed, from cache: #fromCache, up-to-date #upToDate)"() {
        when:
        reporter.buildFinished(new TaskExecutionStatistics(executed, fromCache, upToDate, 0))

        then:
        TextUtil.normaliseLineSeparators(textOutputFactory as String) == "{org.gradle.internal.buildevents.BuildResultLogger}{LIFECYCLE}$expected\n"

        where:
        executed | fromCache | upToDate | expected
        2        | 0         | 0        | "2 actionable tasks: 2 executed"
        2        | 1         | 0        | "3 actionable tasks: 2 executed, 1 from cache"
        2        | 0         | 1        | "3 actionable tasks: 2 executed, 1 up-to-date"
        0        | 7         | 0        | "7 actionable tasks: 7 from cache"
        0        | 0         | 5        | "5 actionable tasks: 5 up-to-date"
    }

    @Unroll
    def "does not report time saved or wasted by caching if under #timeSaved"() {
        when:
        reporter.buildFinished(new TaskExecutionStatistics(0, 2, 0, timeSaved))

        then:
        TextUtil.normaliseLineSeparators(textOutputFactory as String) == "{org.gradle.internal.buildevents.BuildResultLogger}{LIFECYCLE}2 actionable tasks: 2 from cache\n"

        where:
        timeSaved << [0, 100, 499, -100, -499]
    }

    @Unroll
    def "reports time saved or wasted by caching as: #expected"() {
        when:
        reporter.buildFinished(new TaskExecutionStatistics(0, 2, 0, timeSaved))

        then:
        TextUtil.normaliseLineSeparators(textOutputFactory as String) == "{org.gradle.internal.buildevents.BuildResultLogger}{LIFECYCLE}2 actionable tasks: 2 from cache\n$expected\n"

        where:
        timeSaved | expected
        750       | "Using the build cache saved 0.75s in this build"
        1250      | "Using the build cache saved 1s in this build"
        1750      | "Using the build cache saved 1s in this build"
        62000     | "Using the build cache saved 1m 2s in this build"
        -750      | "Without the build cache this build would have been 0.75s faster"
        -1250     | "Without the build cache this build would have been 1s faster"
        -1750     | "Without the build cache this build would have been 1s faster"
        -62000    | "Without the build cache this build would have been 1m 2s faster"
    }
}
