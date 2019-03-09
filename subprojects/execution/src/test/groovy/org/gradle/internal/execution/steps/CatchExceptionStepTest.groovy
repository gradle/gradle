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

import org.gradle.internal.execution.Context
import org.gradle.internal.execution.ExecutionException
import org.gradle.internal.execution.IncrementalChangesContext
import org.gradle.internal.execution.Result
import spock.lang.Unroll

class CatchExceptionStepTest extends StepSpec {
    def step = new CatchExceptionStep<Context>(delegate)
    def context = Mock(IncrementalChangesContext)

    def "successful result is preserved"() {
        def delegateResult = Mock(Result)

        when:
        def result = step.execute(context)

        then:
        result == delegateResult

        1 * delegate.execute(context) >> delegateResult
        0 * _
    }

    @Unroll
    def "failure #failure.class.simpleName is caught"() {
        when:
        def result = step.execute(context)

        then:
        result.outcome.failure.get() instanceof ExecutionException
        result.outcome.failure.get().cause == failure

        1 * delegate.execute(context) >> { throw failure }
        1 * context.work >> work
        1 * work.displayName >> "Failing work"
        0 * _

        where:
        failure << [new RuntimeException(), new Error()]
    }
}
