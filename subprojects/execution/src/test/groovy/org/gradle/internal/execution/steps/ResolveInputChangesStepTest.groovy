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


import org.gradle.internal.execution.IncrementalChangesContext
import org.gradle.internal.execution.InputChangesContext
import org.gradle.internal.execution.Result
import org.gradle.internal.execution.Step
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.history.changes.ExecutionStateChanges
import org.gradle.internal.execution.history.changes.InputChangesInternal
import spock.lang.Specification

class ResolveInputChangesStepTest extends Specification {

    def delegateStep = Mock(Step)
    def work = Mock(UnitOfWork)
    def step = new ResolveInputChangesStep<IncrementalChangesContext>(delegateStep)
    def context = Mock(IncrementalChangesContext)
    def changes = Mock(ExecutionStateChanges)
    def optionalChanges = Optional.of(changes)
    def inputChanges = Mock(InputChangesInternal)
    def result = Mock(Result)

    def "resolves input changes when required"() {
        when:
        def returnedResult = step.execute(context)
        then:
        1 * context.work >> work
        1 * work.inputChangeTrackingStrategy >> UnitOfWork.InputChangeTrackingStrategy.INCREMENTAL_PARAMETERS
        1 * context.changes >> optionalChanges
        1 * changes.createInputChanges() >> inputChanges
        1 * inputChanges.incremental >> true
        1 * delegateStep.execute(_) >> { InputChangesContext context ->
            assert context.inputChanges.get() == inputChanges
            return result
        }
        0 * _

        returnedResult == result
    }

    def "do not resolve input changes when not required"() {
        when:
        def returnedResult = step.execute(context)
        then:
        1 * context.work >> work
        1 * work.inputChangeTrackingStrategy >> UnitOfWork.InputChangeTrackingStrategy.NONE
        1 * delegateStep.execute(_) >> { InputChangesContext context ->
            assert context.inputChanges == Optional.empty()
            return result
        }
        0 * _

        returnedResult == result
    }
}
