/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.declarativedsl.conventions

import org.gradle.internal.declarativedsl.analysis.AnalysisStatementFilter
import org.gradle.internal.declarativedsl.analysis.ResolutionResult
import org.gradle.internal.declarativedsl.analysis.and
import org.gradle.internal.declarativedsl.analysis.implies
import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchema
import org.gradle.internal.declarativedsl.evaluationSchema.InterpretationSequenceStep
import org.gradle.internal.declarativedsl.language.FunctionCall
import org.gradle.plugin.software.internal.SoftwareTypeRegistry

private const val CONVENTIONS = "conventions"

class ConventionsInterpretationSequenceStep(
    override val stepIdentifier: String = CONVENTIONS,
    private val softwareTypeRegistry: SoftwareTypeRegistry,
    private val buildEvaluationSchema: () -> EvaluationSchema
) : InterpretationSequenceStep<ConventionsTopLevelReceiver> {
    private val conventionsResolutionProcessor = ConventionsResolutionProcessor()

    override fun evaluationSchemaForStep(): EvaluationSchema = buildEvaluationSchema()

    override fun getTopLevelReceiverFromTarget(target: Any): ConventionsTopLevelReceiver = ConventionsTopLevelReceiver()

    override fun whenResolved(resolutionResult: ResolutionResult) {
        val conventions = conventionsResolutionProcessor.process(resolutionResult)
        softwareTypeRegistry.softwareTypeImplementations.forEach { softwareTypeImplementation ->
            val functionCall = conventions[softwareTypeImplementation.softwareType]
            if (functionCall != null) {
                softwareTypeImplementation.addConvention(AssignmentRecordConvention(functionCall))
            }
        }
    }

    override fun whenEvaluated(resultReceiver: ConventionsTopLevelReceiver) {

    }
}

val isConventionsConfiguringCall: AnalysisStatementFilter = AnalysisStatementFilter.isConfiguringCall.and(AnalysisStatementFilter.isCallNamed(CONVENTIONS))

val isSoftwareTypeConfiguringCall: AnalysisStatementFilter = AnalysisStatementFilter { _, scopes ->
    scopes.any { it.receiver.originElement is FunctionCall && (it.receiver.originElement as FunctionCall).name == CONVENTIONS }
}

internal
val isTopLevelConventionsBlock: AnalysisStatementFilter = AnalysisStatementFilter.isTopLevelElement.implies(isConventionsConfiguringCall)
