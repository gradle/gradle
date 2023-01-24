/*
 * Copyright 2020 the original author or authors.
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

import com.google.common.collect.ImmutableSet
import com.google.common.collect.ImmutableSortedMap
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.InputFingerprinter
import org.gradle.internal.execution.impl.DefaultInputFingerprinter
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.snapshot.ValueSnapshot

class IdentifyStepTest extends StepSpec<ExecutionRequestContext> {
    def delegateResult = Mock(Result)
    def inputFingerprinter = Mock(InputFingerprinter)
    def step = new IdentifyStep<>(buildOperationExecutor, delegate)


    def "delegates with assigned workspace"() {
        def inputSnapshot = Mock(ValueSnapshot)
        def inputFilesFingerprint = Mock(CurrentFileCollectionFingerprint)

        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult
        _ * work.getInputFingerprinter() >> inputFingerprinter

        1 * inputFingerprinter.fingerprintInputProperties(
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of(),
            _
        ) >> new DefaultInputFingerprinter.InputFingerprints(
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of("input", inputSnapshot),
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of("input-files", inputFilesFingerprint),
            ImmutableSet.of()
        )

        1 * delegate.execute(work, _ as IdentityContext) >> { UnitOfWork work, IdentityContext delegateContext ->
            assert delegateContext.inputProperties as Map == ["input": inputSnapshot]
            assert delegateContext.inputFileProperties as Map == ["input-files": inputFilesFingerprint]
            delegateResult
        }
    }
}
