/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.declarativedsl.project

import org.gradle.internal.declarativedsl.analysis.analyzeEverything
import org.gradle.internal.declarativedsl.common.dependencyCollectors
import org.gradle.internal.declarativedsl.common.gradleDslGeneralSchema
import org.gradle.internal.declarativedsl.evaluationSchema.DefaultInterpretationSequence
import org.gradle.internal.declarativedsl.evaluationSchema.buildEvaluationAndConversionSchema
import org.gradle.internal.declarativedsl.evaluationSchema.ifConversionSupported
import org.gradle.internal.declarativedsl.evaluator.conversion.EvaluationAndConversionSchema
import org.gradle.internal.declarativedsl.software.softwareTypesComponent
import org.gradle.plugin.software.internal.SoftwareTypeRegistry


internal
fun projectInterpretationSequence(
    softwareTypeRegistry: SoftwareTypeRegistry
) = DefaultInterpretationSequence(listOf(projectInterpretationSequenceStep(softwareTypeRegistry)))


fun projectEvaluationSchema(
    softwareTypeRegistry: SoftwareTypeRegistry,
): EvaluationAndConversionSchema {
    return buildEvaluationAndConversionSchema(ProjectTopLevelReceiver::class, analyzeEverything) {
        gradleDslGeneralSchema()
        dependencyCollectors()
        ifConversionSupported {
            softwareTypesComponent(ProjectTopLevelReceiver::class, softwareTypeRegistry, withDefaultsApplication = true)
        }
    }
}
