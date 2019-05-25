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
import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.execution.Context
import org.gradle.internal.execution.Result

class CancelExecutionStepTest extends StepSpec {
    def cancellationToken = Mock(BuildCancellationToken)
    def step = new CancelExecutionStep<Context>(cancellationToken, delegate)
    def context = Mock(Context)
    def delegateResult = Mock(Result)

    def "executes normally when cancellation is not requested"() {
        when:
        def result = step.execute(context)

        then:
        result == delegateResult

        1 * delegate.execute(context) >> delegateResult

        then:
        1 * cancellationToken.cancellationRequested >> false
        0 *_
    }

    def "cancels execution when cancellation is requested"() {
        when:
        step.execute(context)

        then:
        thrown BuildCancelledException

        1 * delegate.execute(context) >> delegateResult

        then:
        1 * cancellationToken.cancellationRequested >> true
        1 * context.work >> work
        1 * work.displayName >> "cancelled work"
        0 *_
    }
}
