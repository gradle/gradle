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

package org.gradle.api.internal.tasks.execution

import com.google.common.collect.ImmutableSortedMap
import com.google.common.collect.ImmutableSortedSet
import org.gradle.api.internal.OverlappingOutputs
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskExecuterResult
import org.gradle.api.internal.tasks.TaskExecutionContext
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.api.internal.tasks.properties.OutputFilePropertySpec
import org.gradle.api.internal.tasks.properties.TaskProperties
import org.gradle.internal.execution.history.AfterPreviousExecutionState
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint
import org.gradle.internal.fingerprint.impl.AbsolutePathFingerprintingStrategy
import spock.lang.Specification

class ResolveBeforeExecutionOutputsTaskExecuterTest extends Specification {
    def delegate = Mock(TaskExecuter)
    def taskFingerprinter = Mock(TaskFingerprinter)
    def executer = new ResolveBeforeExecutionOutputsTaskExecuter(taskFingerprinter, delegate)

    def taskProperties = Mock(TaskProperties)
    def outputFileProperties = ImmutableSortedSet.<OutputFilePropertySpec>of()
    def afterPreviousExecutionState = Mock(AfterPreviousExecutionState)
    def task = Mock(TaskInternal)
    def state = Mock(TaskStateInternal)
    def context = Mock(TaskExecutionContext)

    def setup() {
        context.taskProperties >> taskProperties
        taskProperties.outputFileProperties >> outputFileProperties
        context.afterPreviousExecution >> afterPreviousExecutionState
    }

    def "declares no overlaps when there are none"() {
        given:
        def outputFilesAfterPreviousExecution = ImmutableSortedMap.of(
            "output", AbsolutePathFingerprintingStrategy.INCLUDE_MISSING.emptyFingerprint
        )
        afterPreviousExecutionState.outputFileProperties >> outputFilesAfterPreviousExecution
        def outputFilesBeforeExecution = ImmutableSortedMap.of(
            "output", AbsolutePathFingerprintingStrategy.INCLUDE_MISSING.emptyFingerprint
        )
        taskFingerprinter.fingerprintTaskFiles(task, outputFileProperties) >> outputFilesBeforeExecution

        when:
        executer.execute(task, state, context)

        then:
        1 * context.setOutputFilesBeforeExecution(outputFilesBeforeExecution)
        1 * delegate.execute(task, state, context) >> TaskExecuterResult.WITHOUT_OUTPUTS
        0 * context.setOverlappingOutputs(_ as OverlappingOutputs)
    }

    def "declares overlaps when there is one"() {
        given:
        def outputFilesAfterPreviousExecution = ImmutableSortedMap.of(
            "output", AbsolutePathFingerprintingStrategy.INCLUDE_MISSING.emptyFingerprint
        )
        afterPreviousExecutionState.outputFileProperties >> outputFilesAfterPreviousExecution
        def beforeExecutionOutputFingerprints = Mock(CurrentFileCollectionFingerprint)
        beforeExecutionOutputFingerprints.fingerprints >> ImmutableSortedMap.of(
            "file", Mock(FileSystemLocationFingerprint)
        )
        def outputFilesBeforeExecution = ImmutableSortedMap.<String, CurrentFileCollectionFingerprint>of(
            "output", beforeExecutionOutputFingerprints
        )
        taskFingerprinter.fingerprintTaskFiles(task, outputFileProperties) >> outputFilesBeforeExecution

        when:
        executer.execute(task, state, context)

        then:
        1 * context.setOutputFilesBeforeExecution(outputFilesBeforeExecution)
        1 * delegate.execute(task, state, context) >> TaskExecuterResult.WITHOUT_OUTPUTS
    }
}
