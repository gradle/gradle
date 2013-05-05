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
    private final TaskStateInternal state = new TaskStateInternal('task-description')

    @Test
    public void defaultValues() {
        assertFalse(state.getExecuted())
        assertFalse(state.getExecuting())
        assertTrue(state.configurable)
        assertThat(state.getFailure(), nullValue())
        assertFalse(state.getDidWork())
        assertFalse(state.getSkipped())
        assertThat(state.getSkipMessage(), nullValue())
    }

    @Test
    public void canMarkTaskAsExecuted() {
        state.executed()
        assertTrue(state.executed)
        assertFalse(state.skipped)
        assertFalse(state.configurable)
        assertThat(state.getFailure(), nullValue())
    }
    
    @Test
    public void canMarkTaskAsExecutedWithFailure() {
        RuntimeException failure = new RuntimeException()
        state.executed(failure)
        assertTrue(state.executed)
        assertFalse(state.skipped)
        assertFalse(state.configurable)
        assertThat(state.failure, sameInstance(failure))
    }

    @Test
    public void canMarkTaskAsSkipped() {
        state.skipped('skip-message')
        assertTrue(state.executed)
        assertTrue(state.skipped)
        assertFalse(state.configurable)
        assertThat(state.skipMessage, equalTo('skip-message'))
    }

    @Test
    public void canMarkTaskAsUpToDate() {
        state.upToDate()
        assertTrue(state.executed)
        assertTrue(state.skipped)
        assertFalse(state.configurable)
        assertThat(state.skipMessage, equalTo('UP-TO-DATE'))
    }

    @Test
    public void rethrowFailureDoesNothingWhenTaskHasNotExecuted() {
        state.rethrowFailure()
    }

    @Test
    public void rethrowFailureDoesNothingWhenTaskDidNotFail() {
        state.executed()
        state.rethrowFailure()
    }

    @Test
    public void rethrowsFailureWhenFailureIsRuntimeException() {
        RuntimeException failure = new RuntimeException()
        state.executed(failure)
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
        state.executed(failure)
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
        state.executed(failure)
        try {
            state.rethrowFailure()
            fail()
        } catch (GradleException e) {
            assertThat(e.message, equalTo('Task-description failed with an exception.'))
            assertThat(e.cause, sameInstance(failure))
        }
    }
}
