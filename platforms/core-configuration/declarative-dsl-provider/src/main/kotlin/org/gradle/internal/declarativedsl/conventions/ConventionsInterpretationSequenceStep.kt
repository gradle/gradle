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

import org.gradle.declarative.dsl.evaluation.AnalysisStatementFilter
import org.gradle.declarative.dsl.evaluation.EvaluationSchema
import org.gradle.internal.declarativedsl.analysis.AnalysisStatementFilterUtils.isCallNamed
import org.gradle.internal.declarativedsl.analysis.AnalysisStatementFilterUtils.isConfiguringCall
import org.gradle.internal.declarativedsl.analysis.AnalysisStatementFilterUtils.isTopLevelElement
import org.gradle.internal.declarativedsl.analysis.DefaultOperationGenerationId
import org.gradle.internal.declarativedsl.analysis.and
import org.gradle.internal.declarativedsl.analysis.implies
import org.gradle.internal.declarativedsl.common.dependencyCollectors
import org.gradle.internal.declarativedsl.common.gradleDslGeneralSchema
import org.gradle.internal.declarativedsl.evaluationSchema.SimpleInterpretationSequenceStep
import org.gradle.internal.declarativedsl.evaluationSchema.buildEvaluationSchema
import org.gradle.internal.declarativedsl.evaluator.conventions.ConventionDefinition
import org.gradle.internal.declarativedsl.evaluator.conventions.ConventionsConfiguringBlock
import org.gradle.internal.declarativedsl.evaluator.conventions.ConventionsTopLevelReceiver
import org.gradle.internal.declarativedsl.software.softwareTypesConventions
import org.gradle.plugin.software.internal.SoftwareTypeRegistry


internal
fun conventionsDefinitionInterpretationSequenceStep(softwareTypeRegistry: SoftwareTypeRegistry) = SimpleInterpretationSequenceStep(
    "settingsConventions",
    features = setOf(ConventionDefinition()),
    buildEvaluationAndConversionSchema = { conventionsEvaluationSchema(softwareTypeRegistry) }
)


private
fun conventionsEvaluationSchema(softwareTypeRegistry: SoftwareTypeRegistry): EvaluationSchema =
    buildEvaluationSchema(
        ConventionsTopLevelReceiver::class,
        isTopLevelElement.implies(isConventionsConfiguringCall),
        operationGenerationId = DefaultOperationGenerationId.convention
    ) {
        gradleDslGeneralSchema()
        dependencyCollectors()
        softwareTypesConventions(ConventionsConfiguringBlock::class, softwareTypeRegistry)
    }


val isConventionsConfiguringCall: AnalysisStatementFilter =
    isConfiguringCall.and(isCallNamed(ConventionsTopLevelReceiver::conventions.name))
