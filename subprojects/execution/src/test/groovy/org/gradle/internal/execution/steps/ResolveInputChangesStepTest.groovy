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

import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.history.changes.ExecutionStateChanges
import org.gradle.internal.execution.history.changes.InputChangesInternal

class ResolveInputChangesStepTest extends StepSpec<IncrementalChangesContext> {

    def step = new ResolveInputChangesStep<>(delegate)
    def changes = Mock(ExecutionStateChanges)
    def optionalChanges = Optional.of(changes)
    def inputChanges = Mock(InputChangesInternal)
    def result = Mock(Result)


    def "resolves input changes when required"() {
        when:
        def returnedResult = step.execute(work, context)
        then:
        _ * work.executionBehavior >> UnitOfWork.ExecutionBehavior.INCREMENTAL
        _ * context.changes >> optionalChanges
        1 * changes.createInputChanges() >> inputChanges
        1 * inputChanges.incremental >> true
        1 * delegate.execute(work, _ as InputChangesContext) >> { UnitOfWork work, InputChangesContext context ->
            assert context.inputChanges.get() == inputChanges
            return result
        }
        0 * _

        returnedResult == result
    }

    def "do not resolve input changes when not required"() {
        when:
        def returnedResult = step.execute(work, context)
        then:
        _ * work.executionBehavior >> UnitOfWork.ExecutionBehavior.NON_INCREMENTAL
        1 * delegate.execute(work, _ as InputChangesContext) >> { UnitOfWork work, InputChangesContext context ->
            assert context.inputChanges == Optional.empty()
            return result
        }
        0 * _

        returnedResult == result
    }
}
