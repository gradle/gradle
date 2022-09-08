/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.execution.steps

class EventFiringStepTest extends StepSpec<Context> {
    def step = new EventFiringStep<>(delegate)

    @Override
    protected Context createContext() {
        Stub(Context)
    }

    enum State {
        NOTHING_CALLED,
        BEFORE_CALLED,
        EXECUTED,
        AFTER_CALLED
    }

    def "fires legacy events"() {
        def delegateResult = Mock(Result)
        def state = State.NOTHING_CALLED
        work.fireLegacyEventsBeforeExecution() >> {
            assert state == State.NOTHING_CALLED
            state = State.BEFORE_CALLED
        }
        work.fireLegacyEventsAfterExecution() >> {
            assert state == State.EXECUTED
            state = State.AFTER_CALLED
        }

        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult
        state == State.AFTER_CALLED

        1 * delegate.execute(work, context) >> {
            assert state == State.BEFORE_CALLED
            state = State.EXECUTED
            delegateResult
        }
        0 * _
    }
}
