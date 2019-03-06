/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.execution.impl.steps

import org.gradle.internal.change.ChangeVisitor
import org.gradle.internal.change.DescriptiveChange
import org.gradle.internal.execution.ExecutionOutcome
import org.gradle.internal.execution.Step
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.history.changes.ExecutionStateChanges
import org.gradle.testing.internal.util.Specification

class SkipUpToDateStepTest extends Specification {
    def delegate = Mock(Step)
    def step = new SkipUpToDateStep<IncrementalChangesContext>(delegate)
    def context = Mock(IncrementalChangesContext)
    def work = Mock(UnitOfWork)
    def changes = Mock(ExecutionStateChanges)

    def "skips when outputs are up to date"() {
        when:
        def result = step.execute(context)

        then:
        result.outcome.get() == ExecutionOutcome.UP_TO_DATE
        result.executionReasons.empty

        1 * context.changes >> Optional.of(changes)
        1 * changes.visitAllChanges(_) >> {}
        0 * _
    }

    def "executes when outputs are not up to date"() {
        when:
        def result = step.execute(context)

        then:
        result.executionReasons == ["change"]

        1 * context.getWork() >> work
        1 * context.changes >> Optional.of(changes)
        1 * changes.visitAllChanges(_) >> { ChangeVisitor visitor ->
            visitor.visitChange(new DescriptiveChange("change"))
        }
        1 * delegate.execute(context)
        0 * _
    }

    def "executes when there's no history available"() {
        when:
        def result = step.execute(context)

        then:
        result.executionReasons == ["No history is available."]

        1 * context.getWork() >> work
        1 * context.changes >> Optional.empty()
        1 * delegate.execute(context)
        0 * _
    }
}
