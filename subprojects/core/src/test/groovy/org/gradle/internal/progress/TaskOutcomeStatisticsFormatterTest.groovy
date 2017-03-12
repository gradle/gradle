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

import static org.gradle.api.internal.tasks.TaskExecutionOutcome.*

@Subject(TaskOutcomeStatisticsFormatter)
class TaskOutcomeStatisticsFormatterTest extends Specification {
    TaskOutcomeStatisticsFormatter formatter

    def setup() {
        formatter = new TaskOutcomeStatisticsFormatter()
    }

    def "groups non-executed work as AVOIDED"() {
        expect:
        formatter.incrementAndGetProgress(taskSucceeded(UP_TO_DATE)) == " [100% AVOIDED, 0% DONE]"
        formatter.incrementAndGetProgress(taskSucceeded(FROM_CACHE)) == " [100% AVOIDED, 0% DONE]"
        formatter.incrementAndGetProgress(taskSucceeded(NO_SOURCE)) == " [100% AVOIDED, 0% DONE]"
        formatter.incrementAndGetProgress(taskSucceeded(SKIPPED)) == " [100% AVOIDED, 0% DONE]"
    }

    def "formats executed tasks as DONE"() {
        expect:
        formatter.incrementAndGetProgress(taskSucceeded(EXECUTED)) == " [0% AVOIDED, 100% DONE]"
    }

    def "displays failed tasks"() {
        expect:
        formatter.incrementAndGetProgress(taskFailed()) == " [1 FAILED, 0% AVOIDED, 100% DONE]"
        formatter.incrementAndGetProgress(taskFailed()) == " [2 FAILED, 0% AVOIDED, 100% DONE]"
    }

    def "formats multiple outcome types"() {
        expect:
        formatter.incrementAndGetProgress(taskSucceeded(SKIPPED)) == " [100% AVOIDED, 0% DONE]"
        formatter.incrementAndGetProgress(taskSucceeded(UP_TO_DATE)) == " [100% AVOIDED, 0% DONE]"
        formatter.incrementAndGetProgress(taskSucceeded(FROM_CACHE)) == " [100% AVOIDED, 0% DONE]"
        formatter.incrementAndGetProgress(taskSucceeded(NO_SOURCE)) == " [100% AVOIDED, 0% DONE]"
        formatter.incrementAndGetProgress(taskSucceeded(EXECUTED)) == " [80% AVOIDED, 20% DONE]"
        formatter.incrementAndGetProgress(taskSucceeded(UP_TO_DATE)) == " [83% AVOIDED, 17% DONE]"
    }

    private TaskState taskSucceeded(TaskExecutionOutcome taskExecutionOutcome) {
        def state = new TaskStateInternal('')
        state.outcome = taskExecutionOutcome
        state
    }

    private TaskState taskFailed() {
        def state = new TaskStateInternal('')
        state.outcome = new RuntimeException('boom')
        state
    }
}
