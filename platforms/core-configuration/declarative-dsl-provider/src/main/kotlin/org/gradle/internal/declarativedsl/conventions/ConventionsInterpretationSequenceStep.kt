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

import org.gradle.declarative.dsl.schema.ExternalObjectProviderKey
import org.gradle.internal.declarativedsl.analysis.AnalysisStatementFilter
import org.gradle.internal.declarativedsl.analysis.AssignmentGenerationId
import org.gradle.internal.declarativedsl.analysis.ResolutionResult
import org.gradle.internal.declarativedsl.analysis.and
import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchema
import org.gradle.internal.declarativedsl.evaluationSchema.InterpretationSequenceStep
import org.gradle.internal.declarativedsl.mappingToJvm.ReflectionToObjectConverter
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeCustomAccessors
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeFunctionResolver
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimePropertyResolver
import org.gradle.internal.declarativedsl.objectGraph.ObjectReflection
import org.gradle.plugin.software.internal.SoftwareTypeRegistry


private
const val CONVENTIONS = "conventions"


class ConventionsInterpretationSequenceStep(
    override val stepIdentifier: String = CONVENTIONS,
    override val assignmentGeneration: AssignmentGenerationId = AssignmentGenerationId.CONVENTION_ASSIGNMENT,
    private val softwareTypeRegistry: SoftwareTypeRegistry,
    private val buildEvaluationSchema: () -> EvaluationSchema
) : InterpretationSequenceStep<ConventionsTopLevelReceiver> {
    private
    val conventionsResolutionProcessor = ConventionsResolutionProcessor()

    override fun evaluationSchemaForStep(): EvaluationSchema = buildEvaluationSchema()

    override fun getTopLevelReceiverFromTarget(target: Any): ConventionsTopLevelReceiver = object : ConventionsTopLevelReceiver {
        override fun conventions(conventions: ConventionsConfiguringBlock.() -> Unit) = Unit
    }

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

    override fun whenEvaluated(resultReceiver: ConventionsTopLevelReceiver) = Unit

    override fun getReflectionToObjectConverter(
        externalObjectsMap: Map<ExternalObjectProviderKey, Any>,
        topLevelObject: Any,
        functionResolver: RuntimeFunctionResolver,
        propertyResolver: RuntimePropertyResolver,
        customAccessors: RuntimeCustomAccessors
    ): ReflectionToObjectConverter {
        return object : ReflectionToObjectConverter {
            override fun apply(objectReflection: ObjectReflection, conversionFilter: ReflectionToObjectConverter.ConversionFilter) {
                // Do nothing
            }
        }
    }
}


val isConventionsConfiguringCall: AnalysisStatementFilter = AnalysisStatementFilter.isConfiguringCall.and(AnalysisStatementFilter.isCallNamed(CONVENTIONS))
