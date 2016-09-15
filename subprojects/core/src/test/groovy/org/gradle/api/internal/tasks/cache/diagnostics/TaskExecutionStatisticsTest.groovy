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

import org.apache.commons.lang.RandomStringUtils
import org.gradle.api.Task
import org.gradle.api.execution.TaskOutputCacheListener
import spock.lang.Specification
import spock.lang.Subject

import static org.gradle.api.execution.TaskOutputCacheListener.NotCachedReason.*

@Subject(TaskExecutionStatistics)
class TaskExecutionStatisticsTest extends Specification {
    private diagnostics = new TaskExecutionStatistics()
    private Random random = new Random()

    def 'cached tasks are reported'() {
        given:

        diagnostics.event(cached())
        diagnostics.event(cached())
        diagnostics.event(notCached())
        diagnostics.event(notCached())
        diagnostics.event(cached())

        expect:
        diagnostics.getCachedTasksCount() == 3
    }

    def 'executed tasks are reported'() {
        given:
        3.times { diagnostics.event(cached()) }
        2.times { diagnostics.event(notCached()) }
        7.times { diagnostics.event(cached()) }

        expect:
        diagnostics.executedTasksCount == 12
    }

    def 'cacheable tasks are reported'() {
        given:
        [cached(), notCacheable(), notCacheable(), notInCache(), notInCache(), cached(), notCacheable()].each { diagnostics.event(it) }

        expect:
        diagnostics.cacheableTasksCount == 4

    }

    private TaskCached cached() {
        new TaskCached(task())
    }

    private TaskNotCached notCached() {
        new TaskNotCached(task(), randomIn(TaskOutputCacheListener.NotCachedReason.values()))
    }

    private TaskNotCached notInCache() {
        new TaskNotCached(task(), NOT_IN_CACHE)
    }

    private TaskNotCached notCacheable() {
        new TaskNotCached(task(), randomIn(MULTIPLE_OUTPUTS, NO_OUTPUTS, NOT_CACHEABLE))
    }

    @SafeVarargs
    private final <T> T randomIn(T... values) {
        return values[random.nextInt(values.size())]
    }

    private Task task() {
        task(RandomStringUtils.random(8, true, false))
    }

    private Task task(String name) {
        def task = Mock(Task)
        task.getName() >> name
        task.getClass() >> Task
        task
    }

}
