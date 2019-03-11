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

import org.gradle.internal.execution.Context
import org.gradle.internal.execution.CurrentSnapshotResult
import org.gradle.internal.execution.history.OutputFilesRepository

class RecordOutputsStepTest extends StepSpec implements FingerprinterFixture {
    def outputFilesRepository = Mock(OutputFilesRepository)
    def step = new RecordOutputsStep<Context>(outputFilesRepository, delegate)

    def outputFile = file("output.txt").text = "output"
    def finalOutputs = fingerprintsOf(output: outputFile)

    def context = Mock(Context)
    def delegateResult = Mock(CurrentSnapshotResult)

    def "outputs are recorded after execution"() {
        when:
        def result = step.execute(context)

        then:
        result == delegateResult
        1 * delegate.execute(context) >> delegateResult

        then:
        1 * delegateResult.finalOutputs >> finalOutputs

        then:
        1 * outputFilesRepository.recordOutputs(finalOutputs.values())
        0 * _
    }
}
