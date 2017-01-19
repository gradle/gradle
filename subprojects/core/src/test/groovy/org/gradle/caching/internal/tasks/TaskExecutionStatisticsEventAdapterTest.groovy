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

package org.gradle.caching.internal.tasks

import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.TaskOutputCaching
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.caching.internal.tasks.statistics.TaskExecutionStatistics
import org.gradle.caching.internal.tasks.statistics.TaskExecutionStatisticsListener
import spock.lang.Specification
import spock.lang.Subject

import static org.gradle.api.internal.tasks.TaskExecutionOutcome.*

@Subject(TaskExecutionStatisticsEventAdapter)
class TaskExecutionStatisticsEventAdapterTest extends Specification {
    def listener = Mock(TaskExecutionStatisticsListener)
    def statisticsEventAdapter = new TaskExecutionStatisticsEventAdapter(listener)

    def "test"() {
        given:
        [
            [FROM_CACHE, true],
            [FROM_CACHE, true],
            [EXECUTED, false],
            [SKIPPED, false],
            [UP_TO_DATE, false],
            [SKIPPED, true],
            [UP_TO_DATE, false],
            [UP_TO_DATE, false],
            [FROM_CACHE, true],
            [EXECUTED, true],
            [UP_TO_DATE, false],
            [UP_TO_DATE, false],
            [FROM_CACHE, true],
            [SKIPPED, false]
        ].each { outcome, cacheable ->
            def task = Mock(TaskInternal)
            def taskOutputCaching = Mock(TaskOutputCaching) {
                isEnabled() >> cacheable
            }
            def state = Mock(TaskStateInternal) {
                getOutcome() >> outcome
                getTaskOutputCaching() >> taskOutputCaching
            }
            statisticsEventAdapter.afterExecute(task, state)
        }

        when:
        statisticsEventAdapter.completed()
        then:
        1 * listener.buildFinished(_) >> { TaskExecutionStatistics statistics ->
            assert statistics.getTasksCount(FROM_CACHE) == 4
            assert statistics.getTasksCount(EXECUTED) == 2
            assert statistics.getTasksCount(SKIPPED) == 3
            assert statistics.getTasksCount(UP_TO_DATE) == 5
            assert statistics.cacheMissCount == 1
        }
        0 * _
    }
}
