/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.tasks.execution
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskExecutionContext
import org.gradle.api.internal.tasks.TaskStateInternal
import spock.lang.Specification

class PostExecutionAnalysisTaskExecuterTest extends Specification {
    def target = Mock(TaskExecuter)
    def task = Mock(TaskInternal)
    def state = Mock(TaskStateInternal)
    def context = Mock(TaskExecutionContext)
    final PostExecutionAnalysisTaskExecuter executer = new PostExecutionAnalysisTaskExecuter(target)

    def marksTaskUpToDateWhenItHasActionsAndItDidNotDoWork() {
        when:
        executer.execute(task, state, context)

        then:
        1 * target.execute(task, state, context)
        1 * state.didWork >> false
        1 * state.upToDate()
        0 * _
    }

    def doesNotMarkTaskUpToDateWhenItHasActionsAndDidWork() {
        when:
        executer.execute(task, state, context)

        then:
        1 * target.execute(task, state, context)
        1 * state.didWork >> true
        0 * _
    }
}
