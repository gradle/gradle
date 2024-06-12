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

import org.gradle.internal.declarativedsl.analysis.ObjectOrigin
import org.gradle.internal.declarativedsl.analysis.SchemaTypeRefContext
import org.gradle.internal.declarativedsl.analysis.emptyResolutionResultForReceiver
import org.gradle.internal.declarativedsl.evaluator.conventions.ConventionApplicationHandler.Companion.processResolutionResultWithConventions
import org.gradle.internal.declarativedsl.language.Block
import org.gradle.internal.declarativedsl.language.SourceData
import org.gradle.internal.declarativedsl.mappingToJvm.CompositeCustomAccessors
import org.gradle.internal.declarativedsl.mappingToJvm.CompositeFunctionResolver
import org.gradle.internal.declarativedsl.mappingToJvm.CompositePropertyResolver
import org.gradle.internal.declarativedsl.mappingToJvm.DeclarativeReflectionToObjectConverter
import org.gradle.internal.declarativedsl.objectGraph.AssignmentResolver
import org.gradle.internal.declarativedsl.objectGraph.AssignmentTracer
import org.gradle.internal.declarativedsl.objectGraph.ReflectionContext
import org.gradle.internal.declarativedsl.objectGraph.reflect
import org.gradle.internal.declarativedsl.project.projectInterpretationSequenceStep
import org.gradle.plugin.software.internal.ConventionHandler
import org.gradle.plugin.software.internal.SoftwareTypeRegistry


class NonDeclarativeConventionHandler(softwareTypeRegistry: SoftwareTypeRegistry) : ConventionHandler {
    private
    val step = projectInterpretationSequenceStep(softwareTypeRegistry)
    private
    val conventionRegistry = softwareTypeRegistryBasedConventionRepositoryWithContext(softwareTypeRegistry)

    override fun apply(target: Any, softwareTypeName: String) {
        val conventionResolutionResults = conventionRegistry.findConventions(softwareTypeName)
        val topLevelBlock = Block(emptyList(), SourceData.NONE)
        val topLevelReceiver = ObjectOrigin.TopLevelReceiver(step.evaluationSchemaForStep.analysisSchema.topLevelReceiverType, topLevelBlock)

        val resolutionResult = processResolutionResultWithConventions(
            emptyResolutionResultForReceiver(topLevelReceiver),
            listOf(conventionResolutionResults).requireNoNulls()
        )
        val trace = AssignmentTracer { AssignmentResolver() }.produceAssignmentTrace(resolutionResult)
        val context = ReflectionContext(
            SchemaTypeRefContext(step.evaluationSchemaForStep.analysisSchema),
            resolutionResult,
            trace
        )
        val propertyResolver = CompositePropertyResolver(step.evaluationSchemaForStep.runtimePropertyResolvers)
        val functionResolver = CompositeFunctionResolver(step.evaluationSchemaForStep.runtimeFunctionResolvers)
        val customAccessors = CompositeCustomAccessors(step.evaluationSchemaForStep.runtimeCustomAccessors)

        val topLevelObjectReflection = reflect(resolutionResult.topLevelReceiver, context)
        val topLevelReceiverObject = step.getTopLevelReceiverFromTarget(target)
        val converter = DeclarativeReflectionToObjectConverter(
            emptyMap(), topLevelReceiverObject, functionResolver, propertyResolver, customAccessors
        )
        converter.apply(topLevelObjectReflection)

        step.whenEvaluated(topLevelReceiver)
    }
}
