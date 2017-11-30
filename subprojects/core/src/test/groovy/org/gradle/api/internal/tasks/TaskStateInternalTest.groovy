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
import spock.lang.Specification

class TaskStateInternalTest extends Specification {
    def state = new TaskStateInternal()

    void defaultValues() {
        expect:
        !state.executed
        !state.executing
        state.configurable
        state.failure == null
        !state.didWork
        !state.skipped
        state.skipMessage == null
        !state.upToDate
        !state.taskOutputCaching.enabled
        state.actionable
    }

    void canMarkTaskAsExecuted() {
        state.recordExecuted()

        expect:
        state.executed
        !state.skipped
        !state.upToDate
        !state.configurable
        state.failure == null
    }

    void canMarkTaskAsExecutedWithFailure() {
        RuntimeException failure = new RuntimeException()
        state.recordFailure(failure)

        expect:
        state.executed
        !state.skipped
        !state.upToDate
        !state.configurable
        state.failure == failure
    }

    void canMarkTaskAsSkipped() {
        state.recordSkipped()

        expect:
        state.executed
        state.skipped
        !state.upToDate
        !state.configurable
        state.skipMessage == "SKIPPED"
    }

    void canMarkTaskAsUpToDate() {
        state.recordUpToDate()

        expect:
        state.executed
        state.skipped
        state.upToDate
        !state.configurable
        state.skipMessage == 'UP-TO-DATE'
    }

    void canMarkTaskAsFromCache() {
        state.recordLoadedFromCache(0L)

        expect:
        state.executed
        state.skipped
        state.upToDate
        !state.configurable
        state.skipMessage == 'FROM-CACHE'
    }

    void rethrowFailureDoesNothingWhenTaskHasNotExecuted() {
        when:
        state.rethrowFailure()
        then:
        noExceptionThrown()
    }

    void rethrowFailureDoesNothingWhenTaskDidNotFail() {
        state.recordExecuted()

        when:
        state.rethrowFailure()
        then:
        noExceptionThrown()
    }

    void rethrowsFailureWhenFailureIsRuntimeException() {
        RuntimeException failure = new RuntimeException()
        state.recordFailure(failure)

        when:
        state.rethrowFailure()
        then:
        def e = thrown RuntimeException
        e == failure
    }

    void rethrowsFailureWhenFailureIsError() {
        Error failure = new Error()
        state.recordFailure(failure)

        when:
        state.rethrowFailure()
        then:
        def e = thrown Error
        e == failure
    }

    void rethrowsFailureWhenFailureIsException() {
        Exception failure = new Exception()
        state.recordFailure(failure)

        when:
        state.rethrowFailure()
        then:
        def e = thrown GradleException
        e.message == 'Task failed with an exception.'
        e.cause == failure
    }
}
