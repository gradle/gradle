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
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.Test
import org.junit.runner.RunWith

import static org.hamcrest.Matchers.sameInstance
import static org.junit.Assert.assertThat
import static org.junit.Assert.fail

@RunWith(JMock.class)
class ExecuteAtMostOnceTaskExecuterTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final TaskExecuter target = context.mock(TaskExecuter.class)
    private final TaskInternal task = context.mock(TaskInternal.class)
    private final TaskStateInternal state = context.mock(TaskStateInternal.class)
    private final TaskExecutionContext executionContext = context.mock(TaskExecutionContext)
    private final ExecuteAtMostOnceTaskExecuter executer = new ExecuteAtMostOnceTaskExecuter(target)

    @Test
    public void doesNothingWhenTaskHasAlreadyBeenExecuted() {
        context.checking {
            allowing(state).getExecuted()
            will(returnValue(true))
        }

        executer.execute(task, state, executionContext)
    }

    @Test
    public void delegatesToExecuterWhenTaskHasNotBeenExecuted() {
        context.checking {
            allowing(state).getExecuted()
            will(returnValue(false))
            one(target).execute(task, state, executionContext)
            one(state).executed()
        }

        executer.execute(task, state, executionContext)
    }

    @Test
    public void marksTaskExecutedOnFailureFromExecuter() {
        def failure = new RuntimeException()

        context.checking {
            allowing(state).getExecuted()
            will(returnValue(false))
            one(target).execute(task, state, executionContext)
            will(throwException(failure))
            one(state).executed()
        }

        try {
            executer.execute(task, state, executionContext)
            fail()
        } catch (RuntimeException e) {
            assertThat(e, sameInstance(failure))
        }
    }
}
