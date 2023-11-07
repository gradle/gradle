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

import com.google.common.collect.ImmutableSet
import com.google.common.collect.ImmutableSortedMap
import org.gradle.internal.execution.InputFingerprinter
import org.gradle.internal.execution.WorkInputListeners
import org.gradle.internal.execution.impl.DefaultInputFingerprinter
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.properties.InputBehavior
import org.gradle.internal.snapshot.ValueSnapshot

import static org.gradle.internal.execution.ExecutionEngine.ExecutionOutcome.SHORT_CIRCUITED
import static org.gradle.internal.properties.InputBehavior.PRIMARY

abstract class AbstractSkipEmptyWorkStepTest<C extends IdentityContext> extends StepSpec<C> {
    def workInputListeners = Mock(WorkInputListeners)
    def inputFingerprinter = Mock(InputFingerprinter)
    def primaryFileInputs = EnumSet.of(PRIMARY)
    def allFileInputs = EnumSet.allOf(InputBehavior)

    def knownSnapshot = Mock(ValueSnapshot)
    def knownFileFingerprint = Mock(CurrentFileCollectionFingerprint)
    def knownInputProperties = ImmutableSortedMap.<String, ValueSnapshot> of()
    def knownInputFileProperties = ImmutableSortedMap.<String, CurrentFileCollectionFingerprint> of()
    def sourceFileFingerprint = Mock(CurrentFileCollectionFingerprint)

    abstract protected AbstractSkipEmptyWorkStep<C> createStep()

    AbstractSkipEmptyWorkStep<C> step

    def setup() {
        step = createStep()
        _ * work.inputFingerprinter >> inputFingerprinter
        context.getInputProperties() >> { knownInputProperties }
        context.getInputFileProperties() >> { knownInputFileProperties }
    }

    def "delegates when work has no source properties"() {
        def delegateResult = Mock(CachingResult)
        knownInputProperties = ImmutableSortedMap.of("known", knownSnapshot)
        knownInputFileProperties = ImmutableSortedMap.of("known-file", knownFileFingerprint)

        when:
        def result = step.execute(work, context)

        then:
        1 * inputFingerprinter.fingerprintInputProperties(
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of(),
            knownInputProperties,
            knownInputFileProperties,
            _
        ) >> new DefaultInputFingerprinter.InputFingerprints(
            knownInputProperties,
            ImmutableSortedMap.of(),
            knownInputFileProperties,
            ImmutableSortedMap.of(),
            ImmutableSet.of())

        then:
        1 * delegate.execute(work, {
            it.inputProperties as Map == ["known": knownSnapshot]
            it.inputFileProperties as Map == ["known-file": knownFileFingerprint]
        }) >> delegateResult
        1 * workInputListeners.broadcastFileSystemInputsOf(work, allFileInputs)
        0 * _

        result == delegateResult
    }

    def "delegates when work has sources"() {
        def delegateResult = Mock(CachingResult)
        def delegateContext = Stub(PreviousExecutionContext)
        knownInputProperties = ImmutableSortedMap.of("known", knownSnapshot)
        knownInputFileProperties = ImmutableSortedMap.of("known-file", knownFileFingerprint)
        context.withInputFiles(ImmutableSortedMap.copyOf("known-file": knownFileFingerprint, "source-file": sourceFileFingerprint)) >> delegateContext

        when:
        def result = step.execute(work, context)

        then:
        1 * inputFingerprinter.fingerprintInputProperties(
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of(),
            knownInputProperties,
            knownInputFileProperties,
            _
        ) >> new DefaultInputFingerprinter.InputFingerprints(
            knownInputProperties,
            ImmutableSortedMap.of(),
            knownInputFileProperties,
            ImmutableSortedMap.of("source-file", sourceFileFingerprint),
            ImmutableSet.of())

        then:
        1 * sourceFileFingerprint.empty >> false

        then:
        1 * delegate.execute(work, delegateContext) >> delegateResult
        1 * workInputListeners.broadcastFileSystemInputsOf(work, allFileInputs)
        0 * _

        then:
        result == delegateResult
    }

    def "skips when work has empty sources"() {
        knownInputProperties = ImmutableSortedMap.of("known", knownSnapshot)
        knownInputFileProperties = ImmutableSortedMap.of("known-file", knownFileFingerprint)

        when:
        def result = step.execute(work, context)

        then:
        1 * inputFingerprinter.fingerprintInputProperties(
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of(),
            knownInputProperties,
            knownInputFileProperties,
            _
        ) >> new DefaultInputFingerprinter.InputFingerprints(
            knownInputProperties,
            ImmutableSortedMap.of(),
            knownInputFileProperties,
            ImmutableSortedMap.of("source-file", sourceFileFingerprint),
            ImmutableSet.of())

        then:
        1 * sourceFileFingerprint.empty >> true
        1 * workInputListeners.broadcastFileSystemInputsOf(work, primaryFileInputs)

        then:
        result.execution.get().outcome == SHORT_CIRCUITED
        !result.afterExecutionState.present
    }
}
