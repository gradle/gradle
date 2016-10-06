/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.internal.tasks.TaskExecutionOutcome
import org.gradle.api.internal.tasks.cache.statistics.TaskExecutionStatistics
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.gradle.internal.logging.text.TestStyledTextOutputFactory
import org.gradle.util.TextUtil
import spock.lang.Specification

class CacheStatisticsReporterTest extends Specification {
    private StyledTextOutputFactory textOutputFactory = new TestStyledTextOutputFactory()
    private CacheStatisticsReporter statisticsReporter = new CacheStatisticsReporter(textOutputFactory)

    def 'all statistics are reported'() {
        given:
        def statistics = new TaskExecutionStatistics(
            (TaskExecutionOutcome.FROM_CACHE): 3,
            (TaskExecutionOutcome.UP_TO_DATE): 2,
            (TaskExecutionOutcome.SKIPPED): 1,
            (TaskExecutionOutcome.EXECUTED): 4,
            3
        )
        when:
        statisticsReporter.buildFinished(statistics)

        then:
        TextUtil.normaliseLineSeparators(textOutputFactory as String) ==
            """{org.gradle.internal.buildevents.BuildResultLogger}{LIFECYCLE}
              |10 tasks in build, out of which 4 (40%) were executed
              | 1  (10%) skipped
              | 2  (20%) up-to-date
              | 3  (30%) loaded from cache
              | 3  (30%) cache miss
              | 1  (10%) not cacheable
              |""".stripMargin()
    }

    def 'zero counts are not reported'() {
        given:
        def statistics = new TaskExecutionStatistics(
            (TaskExecutionOutcome.FROM_CACHE): 3,
            (TaskExecutionOutcome.EXECUTED): 7,
            2
        )
        when:
        statisticsReporter.buildFinished(statistics)

        then:
        TextUtil.normaliseLineSeparators(textOutputFactory as String) ==
            """{org.gradle.internal.buildevents.BuildResultLogger}{LIFECYCLE}
              |10 tasks in build, out of which 7 (70%) were executed
              | 3  (30%) loaded from cache
              | 2  (20%) cache miss
              | 5  (50%) not cacheable
              |""".stripMargin()
    }

    def 'percentages are rounded'() {
        given:
        def statistics = new TaskExecutionStatistics(
            (TaskExecutionOutcome.UP_TO_DATE): 315,
            (TaskExecutionOutcome.FROM_CACHE): 206,
            (TaskExecutionOutcome.SKIPPED): 75,
            (TaskExecutionOutcome.EXECUTED): 404,
            125
        )
        when:
        statisticsReporter.buildFinished(statistics)

        then:
        TextUtil.normaliseLineSeparators(textOutputFactory as String) ==
            """{org.gradle.internal.buildevents.BuildResultLogger}{LIFECYCLE}
              |1000 tasks in build, out of which 404 (40%) were executed
              |  75   (8%) skipped
              | 315  (32%) up-to-date
              | 206  (21%) loaded from cache
              | 125  (13%) cache miss
              | 279  (28%) not cacheable
              |""".stripMargin()
    }
}
