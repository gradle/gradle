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

package org.gradle.internal.progress

import org.gradle.api.internal.tasks.TaskExecutionOutcome
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.api.tasks.TaskState
import spock.lang.Specification
import spock.lang.Subject

import static org.gradle.api.internal.tasks.TaskExecutionOutcome.EXECUTED
import static org.gradle.api.internal.tasks.TaskExecutionOutcome.FROM_CACHE
import static org.gradle.api.internal.tasks.TaskExecutionOutcome.NO_SOURCE
import static org.gradle.api.internal.tasks.TaskExecutionOutcome.SKIPPED
import static org.gradle.api.internal.tasks.TaskExecutionOutcome.UP_TO_DATE

@Subject(TaskOutcomeStatisticsFormatter)
class TaskOutcomeStatisticsFormatterTest extends Specification {
    TaskOutcomeStatisticsFormatter formatter

    def setup() {
        formatter = new TaskOutcomeStatisticsFormatter(50)
    }

    def "formats tasks that were FROM_CACHE"() {
        expect:
        formatter.incrementAndGetProgress(taskCompleted(FROM_CACHE)) == " [1/50 TASKS, 1 FROM-CACHE]"
    }

    def "maintains a completed task count"() {
        expect:
        formatter.incrementAndGetProgress(taskCompleted(EXECUTED)) == " [1/50 TASKS]"
        formatter.incrementAndGetProgress(taskCompleted(EXECUTED)) == " [2/50 TASKS]"
    }

    def "maintains a failed task count"() {
        expect:
        formatter.incrementAndGetProgress(taskFailed()) == " [1/50 TASKS, 1 FAILED]"
    }

    def "formats multiple outcome types"() {
        expect:
        formatter.incrementAndGetProgress(taskCompleted(SKIPPED)) == " [1/50 TASKS]"
        formatter.incrementAndGetProgress(taskCompleted(UP_TO_DATE)) == " [2/50 TASKS, 1 UP-TO-DATE]"
        formatter.incrementAndGetProgress(taskCompleted(FROM_CACHE)) == " [3/50 TASKS, 1 FROM-CACHE, 1 UP-TO-DATE]"
        formatter.incrementAndGetProgress(taskCompleted(NO_SOURCE)) == " [4/50 TASKS, 1 FROM-CACHE, 1 UP-TO-DATE]"
        formatter.incrementAndGetProgress(taskCompleted(EXECUTED)) == " [5/50 TASKS, 1 FROM-CACHE, 1 UP-TO-DATE]"
        formatter.incrementAndGetProgress(taskCompleted(UP_TO_DATE)) == " [6/50 TASKS, 1 FROM-CACHE, 2 UP-TO-DATE]"
        formatter.incrementAndGetProgress(taskFailed()) == " [7/50 TASKS, 1 FAILED, 1 FROM-CACHE, 2 UP-TO-DATE]"
    }

    private TaskState taskCompleted(TaskExecutionOutcome outcome) {
        def state = new TaskStateInternal('')
        state.setOutcome(outcome)
        state
    }

    private TaskState taskFailed() {
        def state = new TaskStateInternal('')
        state.outcome = new RuntimeException('example')
        state
    }
}
