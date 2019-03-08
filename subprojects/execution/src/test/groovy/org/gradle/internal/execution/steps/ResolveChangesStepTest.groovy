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
import org.gradle.internal.execution.IncrementalContext
import org.gradle.internal.execution.Result

class ResolveChangesStepTest extends StepSpec {
    def step = new ResolveChangesStep<Result>(delegate)
    def context = Mock(IncrementalContext)
    def delegateResult = Mock(Result)

    def "doesn't detect input file changes when rebuild is forced"() {
        when:
        def result = step.execute(context)

        then:
        result == delegateResult

        1 * context.work >> work
        1 * delegate.execute(_ as IncrementalChangesContext) >> { IncrementalChangesContext delegateContext ->
            assert !delegateContext.changes.get().inputFilesChanges.present
            delegateResult
        }
        1 * context.rebuildReason >> Optional.of("force rebuild")
        0 * _
    }
}
