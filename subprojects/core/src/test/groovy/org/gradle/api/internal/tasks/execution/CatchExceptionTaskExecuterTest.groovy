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

package org.gradle.api.internal.tasks.execution

import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskExecuterResult
import org.gradle.api.internal.tasks.TaskExecutionContext
import org.gradle.api.internal.tasks.TaskExecutionOutcome
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.api.tasks.TaskExecutionException
import spock.lang.Specification

class CatchExceptionTaskExecuterTest extends Specification {
    private TaskExecuter delegate = Mock(TaskExecuter)
    private CatchExceptionTaskExecuter executer = new CatchExceptionTaskExecuter(delegate)
    private TaskInternal task = Mock(TaskInternal)
    private TaskStateInternal state = new TaskStateInternal()
    private TaskExecutionContext context = Mock(TaskExecutionContext)

    def 'calls delegate and does nothing'() {
        when:
        executer.execute(task, state, context)

        then:
        1 * delegate.execute(task, state, context) >> {
            state.setOutcome(TaskExecutionOutcome.EXECUTED)
            return TaskExecuterResult.WITHOUT_OUTPUTS
        }
        0 * _
        state.outcome == TaskExecutionOutcome.EXECUTED
        !state.failure
    }

    def 'should catch exception of delegate and set the outcome to failure'() {
        given:
        def failure = new RuntimeException("Failure")

        when:
        executer.execute(task, state, context)

        then:
        1 * delegate.execute(task, state, context) >> {
            throw failure
        }
        0 * _

        state.outcome == TaskExecutionOutcome.EXECUTED
        state.failure instanceof TaskExecutionException
        state.failure.cause.is(failure)
    }
}
