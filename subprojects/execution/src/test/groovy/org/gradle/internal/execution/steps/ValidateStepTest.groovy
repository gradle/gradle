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
import org.gradle.internal.execution.Result

class ValidateStepTest extends StepSpec {
    def step = new ValidateStep<Context, Result>(delegate)
    def context = Mock(Context)
    def delegateResult = Mock(Result)

    def "executes work when there are no violations"() {
        when:
        def result = step.execute(context)

        then:
        result == delegateResult

        then:
        1 * context.work >> work
        1 * delegate.execute(context) >> delegateResult
        1 * work.validate()
        0 * _
    }

    def "propagates failure when there are violations"() {
        def failure = new RuntimeException("failure")

        when:
        step.execute(context)

        then:
        def ex = thrown Exception
        ex == failure

        1 * context.work >> work
        1 * work.validate() >> {
            throw failure
        }
        0 * _
    }
}
