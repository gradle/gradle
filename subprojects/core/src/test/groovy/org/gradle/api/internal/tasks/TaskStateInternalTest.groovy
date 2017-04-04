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




package org.gradle.api.internal.tasks

import org.gradle.api.GradleException
import org.junit.Test

import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

class TaskStateInternalTest {
    private final TaskStateInternal state = new TaskStateInternal()

    @Test
    public void defaultValues() {
        assertFalse(state.getExecuted())
        assertFalse(state.getExecuting())
        assertTrue(state.configurable)
        assertThat(state.getFailure(), nullValue())
        assertFalse(state.getDidWork())
        assertFalse(state.getSkipped())
        assertThat(state.getSkipMessage(), nullValue())
        assertFalse(state.upToDate)
        assertFalse(state.taskOutputCaching.enabled)
        assertFalse(state.actionsWereExecuted)
    }

    @Test
    public void canMarkTaskAsExecuted() {
        state.setOutcome(TaskExecutionOutcome.EXECUTED)
        assertTrue(state.executed)
        assertFalse(state.skipped)
        assertFalse(state.upToDate)
        assertFalse(state.configurable)
        assertThat(state.getFailure(), nullValue())
    }

    @Test
    public void canMarkTaskAsExecutedWithFailure() {
        RuntimeException failure = new RuntimeException()
        state.setOutcome(failure)
        assertTrue(state.executed)
        assertFalse(state.skipped)
        assertFalse(state.upToDate)
        assertFalse(state.configurable)
        assertThat(state.failure, sameInstance(failure))
    }

    @Test
    public void canMarkTaskAsSkipped() {
        state.setOutcome(TaskExecutionOutcome.SKIPPED)
        assertTrue(state.executed)
        assertTrue(state.skipped)
        assertFalse(state.upToDate)
        assertFalse(state.configurable)
        assertThat(state.skipMessage, equalTo("SKIPPED"))
    }

    @Test
    public void canMarkTaskAsUpToDate() {
        state.setOutcome(TaskExecutionOutcome.UP_TO_DATE)
        assertTrue(state.executed)
        assertTrue(state.skipped)
        assertTrue(state.upToDate)
        assertFalse(state.configurable)
        assertThat(state.skipMessage, equalTo('UP-TO-DATE'))
    }

    @Test
    public void canMarkTaskAsFromCache() {
        state.setOutcome(TaskExecutionOutcome.FROM_CACHE)
        assertTrue(state.executed)
        assertTrue(state.skipped)
        assertTrue(state.upToDate)
        assertFalse(state.configurable)
        assertThat(state.skipMessage, equalTo('FROM-CACHE'))
    }

    @Test
    public void rethrowFailureDoesNothingWhenTaskHasNotExecuted() {
        state.rethrowFailure()
    }

    @Test
    public void rethrowFailureDoesNothingWhenTaskDidNotFail() {
        state.setOutcome(TaskExecutionOutcome.EXECUTED)
        state.rethrowFailure()
    }

    @Test
    public void rethrowsFailureWhenFailureIsRuntimeException() {
        RuntimeException failure = new RuntimeException()
        state.setOutcome(failure)
        try {
            state.rethrowFailure()
            fail()
        } catch (RuntimeException e) {
            assertThat(e, sameInstance(failure))
        }
    }

    @Test
    public void rethrowsFailureWhenFailureIsError() {
        Error failure = new Error()
        state.setOutcome(failure)
        try {
            state.rethrowFailure()
            fail()
        } catch (Error e) {
            assertThat(e, sameInstance(failure))
        }
    }

    @Test
    public void rethrowsFailureWhenFailureIsException() {
        Exception failure = new Exception()
        state.setOutcome(failure)
        try {
            state.rethrowFailure()
            fail()
        } catch (GradleException e) {
            assertThat(e.message, equalTo('Task failed with an exception.'))
            assertThat(e.cause, sameInstance(failure))
        }
    }
}
