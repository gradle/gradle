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
import spock.lang.Specification

class CacheStatisticsReporterTest extends Specification {
    private StyledTextOutputFactory textOutputFactory = new TestStyledTextOutputFactory()
    private CacheStatisticsReporter statisticsReporter = new CacheStatisticsReporter(textOutputFactory)
    private TaskExecutionStatistics statistics = Mock(TaskExecutionStatistics)

    def 'all statistics are reported'() {
        given:
        recordedTaskStatistics(
            (TaskExecutionOutcome.FROM_CACHE): 3,
            (TaskExecutionOutcome.UP_TO_DATE): 2,
            (TaskExecutionOutcome.SKIPPED): 1,
            (TaskExecutionOutcome.EXECUTED): 4,
            10, 5
        )
        when:
        statisticsReporter.buildFinished(statistics)

        then:
        textOutputFactory.toString() ==
            """{org.gradle.internal.buildevents.BuildResultLogger}{LIFECYCLE}
              |10 tasks in build, out of which 5 (50%) were cacheable
              | 2  (20%) up-to-date
              | 3  (30%) loaded from cache
              | 1  (10%) skipped
              | 4  (40%) executed
              |""".stripMargin().denormalize()
    }

    def 'zero counts are not reported'() {
        given:
        recordedTaskStatistics(
            (TaskExecutionOutcome.FROM_CACHE): 3,
            (TaskExecutionOutcome.EXECUTED): 7,
            10, 0
        )
        when:
        statisticsReporter.buildFinished(statistics)

        then:
        textOutputFactory.toString() ==
            """{org.gradle.internal.buildevents.BuildResultLogger}{LIFECYCLE}
              |10 tasks in build, out of which 0 (0%) were cacheable
              | 3  (30%) loaded from cache
              | 7  (70%) executed
              |""".stripMargin().denormalize()
    }

    def 'percentages are rounded'() {
        given:
        recordedTaskStatistics(
            (TaskExecutionOutcome.UP_TO_DATE): 305,
            (TaskExecutionOutcome.FROM_CACHE): 206,
            (TaskExecutionOutcome.SKIPPED): 75,
            (TaskExecutionOutcome.EXECUTED): 404,
            1000, 206
        )
        when:
        statisticsReporter.buildFinished(statistics)

        then:
        textOutputFactory.toString() ==
            """{org.gradle.internal.buildevents.BuildResultLogger}{LIFECYCLE}
              |1000 tasks in build, out of which 206 (21%) were cacheable
              | 305  (31%) up-to-date
              | 206  (21%) loaded from cache
              |  75   (8%) skipped
              | 404  (40%) executed
              |""".stripMargin().denormalize()
    }

    private void recordedTaskStatistics(Map<TaskExecutionOutcome, Integer> counts, Integer allTasksCount, Integer cacheableTasksCount) {
        interaction {
            statistics.cacheableTasksCount >> cacheableTasksCount
            statistics.allTasksCount >> allTasksCount
            ([
                (TaskExecutionOutcome.FROM_CACHE): 0,
                (TaskExecutionOutcome.UP_TO_DATE): 0,
                (TaskExecutionOutcome.SKIPPED): 0,
                (TaskExecutionOutcome.EXECUTED): 0,
            ] + counts).each { outcome, count ->
                statistics.getTasksCount(outcome) >> count
            }
        }
    }
}
