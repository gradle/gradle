/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.api.BuildCancelledException
import org.gradle.initialization.DefaultBuildCancellationToken

class CancelExecutionStepTest extends StepSpec<Context> {
    def cancellationToken = new DefaultBuildCancellationToken()
    def step = new CancelExecutionStep<Context, Result>(cancellationToken, delegate)
    def delegateResult = Mock(Result)

    def "executes normally when cancellation is not requested"() {
        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult

        1 * delegate.execute(work, context) >> delegateResult

        then:
        0 *_
    }

    def "cancels execution when cancellation is requested"() {
        given:
        cancellationToken.cancel()

        when:
        step.execute(work, context)

        then:
        thrown BuildCancelledException

        1 * delegate.execute(work, context) >> delegateResult

        then:
        0 *_
    }

    def "interrupts task worker when cancellation is requested"() {
        when:
        step.execute(work, context)

        then:
        thrown BuildCancelledException

        1 * delegate.execute(work, context) >>  {
            cancellationToken.cancel()
            wait()
            delegateResult
        }

        then:
        0 *_
    }
}
