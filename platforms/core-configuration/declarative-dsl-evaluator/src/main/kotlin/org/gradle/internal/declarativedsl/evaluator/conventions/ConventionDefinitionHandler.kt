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

package org.gradle.internal.declarativedsl.evaluator.conventions

import org.gradle.declarative.dsl.evaluation.InterpretationStepFeature
import org.gradle.internal.declarativedsl.analysis.ResolutionResult
import org.gradle.internal.declarativedsl.evaluator.features.ResolutionResultHandler
import java.io.Serializable


class ConventionDefinition : InterpretationStepFeature.ResolutionResultPostprocessing.ConventionDefinition, Serializable


class ConventionDefinitionCollector(private val conventionRegistrar: ConventionDefinitionRegistrar) : ResolutionResultHandler {
    override fun shouldHandleFeature(feature: InterpretationStepFeature.ResolutionResultPostprocessing) =
        feature is InterpretationStepFeature.ResolutionResultPostprocessing.ConventionDefinition

    override fun processResolutionResult(resolutionResult: ResolutionResult): ResolutionResult {
        conventionRegistrar.registerConventions(ConventionsResolutionProcessor.process(resolutionResult))
        return resolutionResult
    }
}


interface ConventionDefinitionRegistrar {
    fun registerConventions(conventionsBySoftwareType: Map<String, SoftwareTypeConventionResolutionResults>)
}
