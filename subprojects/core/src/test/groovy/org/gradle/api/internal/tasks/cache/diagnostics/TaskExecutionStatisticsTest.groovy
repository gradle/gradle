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
    private diagnostics = new TaskExecutionStatistics()

    def 'cached tasks are reported'() {
        given:
        diagnostics.event(CACHED)
        diagnostics.event(CACHED)
        4.times { diagnostics.event(EXECUTED) }
        diagnostics.event(CACHED)

        expect:
        diagnostics.getCachedTasksCount() == 3
    }

    def 'all tasks are reported'() {
        given:
        3.times { diagnostics.event(CACHED) }
        4.times { diagnostics.event(EXECUTED) }
        9.times { diagnostics.event(EXECUTED) }
        7.times { diagnostics.event(CACHED) }

        expect:
        diagnostics.allTasksCount == 23
    }

    def 'up to date tasks are reported'() {
        given:
        3.times { diagnostics.event(CACHED) }
        10.times { diagnostics.event(UP_TO_DATE) }
        7.times { diagnostics.event(CACHED) }
        4.times { diagnostics.event(UP_TO_DATE) }

        expect:
        diagnostics.upToDateTaskCount == 14
    }

    def 'skipped tasks are reported'() {
        given:
        3.times { diagnostics.event(CACHED) }
        10.times { diagnostics.event(EXECUTED) }
        5.times { diagnostics.event(SKIPPED) }
        7.times { diagnostics.event(CACHED) }
        4.times { diagnostics.event(EXECUTED) }

        expect:
        diagnostics.skippedTaskCount == 5
    }

    def 'executed tasks are reported'() {
        given:
        3.times { diagnostics.event(CACHED) }
        5.times { diagnostics.event(SKIPPED) }
        7.times { diagnostics.event(EXECUTED) }
        9.times { diagnostics.event(UP_TO_DATE) }
        4.times { diagnostics.event(EXECUTED) }

        expect:
        diagnostics.executedTaskCount == 11
    }

    def 'cacheable tasks are reported'() {
        given:
        [true, true, false, true, false, true].each { diagnostics.taskCacheable(it) }

        expect:
        diagnostics.cacheableTasksCount == 4
    }
}
