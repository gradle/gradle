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
import org.gradle.internal.declarativedsl.analysis.AnalysisStatementFilter.Companion.isTopLevelElement
import org.gradle.internal.declarativedsl.analysis.OperationGenerationId
import org.gradle.internal.declarativedsl.analysis.ResolutionResult
import org.gradle.internal.declarativedsl.analysis.and
import org.gradle.internal.declarativedsl.analysis.implies
import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchema
import org.gradle.internal.declarativedsl.evaluationSchema.InterpretationSequenceStep
import org.gradle.internal.declarativedsl.evaluationSchema.InterpretationStepFeature
import org.gradle.internal.declarativedsl.evaluationSchema.buildEvaluationSchema
import org.gradle.internal.declarativedsl.common.dependencyCollectors
import org.gradle.internal.declarativedsl.common.gradleDslGeneralSchema
import org.gradle.internal.declarativedsl.software.softwareTypesConventions
import org.gradle.plugin.software.internal.SoftwareTypeRegistry


private
const val CONVENTIONS = "conventions"


/**
 * The interpretation step for the top-level "conventions" block in the Settings DSL.  This step extracts the operations
 * in the conventions block and adds them to the software types.  It does no runtime processing of the conventions (i.e.
 * none of the operations are applied by this step).  The stored conventions will be applied later when processing a
 * build file that uses a software type.
 */
internal
class ConventionsInterpretationSequenceStep(
    override val stepIdentifier: String = CONVENTIONS,
    override val assignmentGeneration: OperationGenerationId = OperationGenerationId.CONVENTION_ASSIGNMENT,
    private val softwareTypeRegistry: SoftwareTypeRegistry
) : InterpretationSequenceStep {
    private
    val conventionsResolutionProcessor = ConventionsResolutionProcessor()

    override val features: Set<InterpretationStepFeature>
        get() = setOf(object : InterpretationStepFeature.CollectConventions {})

    override fun evaluationSchemaForStep(): EvaluationSchema = conventionsEvaluationSchema(softwareTypeRegistry)

    override fun processResolutionResult(resolutionResult: ResolutionResult): ResolutionResult {
        val conventions = conventionsResolutionProcessor.process(resolutionResult)
        softwareTypeRegistry.softwareTypeImplementations.forEach { softwareTypeImplementation ->
            conventions.additions[softwareTypeImplementation.softwareType]?.forEach {
                softwareTypeImplementation.addConvention(AdditionRecordConvention(it))
            }
            conventions.assignments[softwareTypeImplementation.softwareType]?.forEach {
                softwareTypeImplementation.addConvention(AssignmentRecordConvention(it))
            }
            conventions.nestedObjectAccess[softwareTypeImplementation.softwareType]?.forEach {
                softwareTypeImplementation.addConvention(NestedObjectAccessConvention(it))
            }
        }
        return resolutionResult
    }
}


private
fun conventionsEvaluationSchema(softwareTypeRegistry: SoftwareTypeRegistry): EvaluationSchema =
    buildEvaluationSchema(
        ConventionsTopLevelReceiver::class,
        isTopLevelElement.implies(isConventionsConfiguringCall)
    ) {
        gradleDslGeneralSchema()
        dependencyCollectors()
        softwareTypesConventions(ConventionsConfiguringBlock::class, softwareTypeRegistry)
    }


val isConventionsConfiguringCall: AnalysisStatementFilter = AnalysisStatementFilter.isConfiguringCall.and(AnalysisStatementFilter.isCallNamed(CONVENTIONS))
