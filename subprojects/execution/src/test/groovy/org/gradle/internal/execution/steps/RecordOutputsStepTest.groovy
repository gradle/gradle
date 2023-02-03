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

import org.gradle.internal.execution.history.AfterExecutionState
import org.gradle.internal.execution.history.OutputFilesRepository

class RecordOutputsStepTest extends StepSpec<Context> implements SnapshotterFixture {
    def outputFilesRepository = Mock(OutputFilesRepository)
    def step = new RecordOutputsStep<>(outputFilesRepository, delegate)

    def outputFile = file("output.txt").text = "output"
    def outputFilesProducedByWork = snapshotsOf(output: outputFile)

    def delegateResult = Mock(AfterExecutionResult)

    def "outputs are recorded after execution"() {
        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult
        1 * delegate.execute(work, context) >> delegateResult

        then:
        1 * delegateResult.afterExecutionState >> Optional.of(Mock(AfterExecutionState) {
            1 * getOutputFilesProducedByWork() >> this.outputFilesProducedByWork
        })

        then:
        1 * outputFilesRepository.recordOutputs(outputFilesProducedByWork.values())
        0 * _
    }

    def "does not store untracked outputs"() {
        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult
        1 * delegate.execute(work, context) >> delegateResult

        then:
        1 * delegateResult.afterExecutionState >> Optional.empty()

        then:
        0 * _
    }
}
