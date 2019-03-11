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

import org.gradle.internal.execution.ExecutionOutcome
import org.gradle.internal.execution.IncrementalChangesContext
import org.gradle.internal.execution.UnitOfWork
import spock.lang.Specification
import spock.lang.Unroll

class ExecuteStepTest extends Specification {
    def step = new ExecuteStep<IncrementalChangesContext>()
    def context = Mock(IncrementalChangesContext)
    def work = Mock(UnitOfWork)

    @Unroll
    def "#outcome outcome is preserved"() {
        when:
        def result = step.execute(context)

        then:
        result.outcome.get() == outcome

        1 * context.work >> work
        1 * work.execute(context) >> { outcome }

        where:
        outcome << ExecutionOutcome.values()
    }

    @Unroll
    def "failure #failure.class.simpleName is not caught"() {
        when:
        def result = step.execute(context)

        then:
        def ex = thrown Throwable
        ex == failure

        1 * context.work >> work
        1 * work.execute(context) >> { throw failure }

        where:
        failure << [new RuntimeException(), new Error()]
    }
}
