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

import com.google.common.collect.ImmutableSortedMap
import org.gradle.internal.execution.UnitOfWork

class CaptureNonIncrementalStateBeforeExecutionStepTest extends AbstractCaptureStateBeforeExecutionStepTest<PreviousExecutionContext> {

    AbstractCaptureStateBeforeExecutionStep<PreviousExecutionContext, CachingResult> step
        = new CaptureNonIncrementalStateBeforeExecutionStep(buildOperationExecutor, classloaderHierarchyHasher, delegate)

    def setup() {
        _ * work.inputFingerprinter >> inputFingerprinter
    }

    def "output file properties are snapshotted as empty"() {
        when:
        step.execute(work, context)

        then:
        interaction { snapshotState() }
        1 * delegate.execute(work, _ as BeforeExecutionContext) >> { UnitOfWork work, BeforeExecutionContext delegateContext ->
            def state = delegateContext.beforeExecutionState.get()
            assert !state.detectedOverlappingOutputs.present
            assert state.outputFileLocationSnapshots == ImmutableSortedMap.of()
        }
        0 * _

        assertOperation()
    }
}
