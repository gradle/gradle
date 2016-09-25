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

package org.gradle.api.internal.tasks.cache.diagnostics

import spock.lang.Specification
import spock.lang.Subject

import static org.gradle.api.internal.tasks.cache.diagnostics.TaskExecutionEvent.*

@Subject(TaskExecutionStatistics)
class TaskExecutionStatisticsTest extends Specification {
    private statistics = new TaskExecutionStatistics()

    def 'tasks states are counted correctly'() {
        given:
        [
            CACHED,
            CACHED,
            EXECUTED,
            SKIPPED,
            UP_TO_DATE,
            SKIPPED,
            UP_TO_DATE,
            UP_TO_DATE,
            CACHED,
            EXECUTED,
            UP_TO_DATE,
            UP_TO_DATE,
            CACHED,
            SKIPPED
        ].each { statistics.event(it) }

        expect:
        statistics.cachedTasksCount == 4
        statistics.executedTasksCount == 2
        statistics.skippedTasksCount == 3
        statistics.upToDateTasksCount == 5
        statistics.allTasksCount == 14
    }

    def 'cacheable tasks are counted correctly'() {
        given:
        [true, true, false, true, false, true].each { statistics.taskCacheable(it) }

        expect:
        statistics.cacheableTasksCount == 4
    }
}
