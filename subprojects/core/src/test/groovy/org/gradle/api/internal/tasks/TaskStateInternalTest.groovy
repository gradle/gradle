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


import org.junit.Test

import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.nullValue
import static org.hamcrest.CoreMatchers.sameInstance
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertThat
import static org.junit.Assert.assertTrue
import static org.junit.Assert.fail

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
        assertTrue(state.actionable)
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
}
