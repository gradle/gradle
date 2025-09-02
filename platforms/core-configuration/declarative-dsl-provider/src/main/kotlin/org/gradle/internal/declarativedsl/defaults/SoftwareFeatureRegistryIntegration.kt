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

package org.gradle.internal.declarativedsl.defaults

import org.gradle.internal.declarativedsl.analysis.AssignmentRecord
import org.gradle.internal.declarativedsl.analysis.DataAdditionRecord
import org.gradle.internal.declarativedsl.analysis.NestedObjectAccessRecord
import org.gradle.internal.declarativedsl.evaluator.defaults.ModelDefaultsDefinitionRegistrar
import org.gradle.internal.declarativedsl.evaluator.defaults.ModelDefaultsRepository
import org.gradle.internal.declarativedsl.evaluator.defaults.ModelDefaultsResolutionResults
import org.gradle.plugin.software.internal.SoftwareFeatureImplementation
import org.gradle.plugin.software.internal.ModelDefault
import org.gradle.plugin.software.internal.SoftwareFeatureRegistry


internal
fun softwareFeatureRegistryBasedModelDefaultsRepository(softwareFeatureRegistry: SoftwareFeatureRegistry): ModelDefaultsRepository = object : ModelDefaultsRepository {
    override fun findDefaults(featureName: String): ModelDefaultsResolutionResults? =
        softwareFeatureRegistry.softwareFeatureImplementations[featureName]?.let { softwareFeature ->
            resolutionResultsFromDefaultsFor(featureName, softwareFeature)
        }
}


private
fun resolutionResultsFromDefaultsFor(featureName: String, softwareFeature: SoftwareFeatureImplementation<*, *>): ModelDefaultsResolutionResults {
    val assignments = buildList {
        softwareFeature.visitModelDefaults(
            AssignmentRecordDefault::class.java,
            ModelDefault.Visitor<AssignmentRecord> { record -> add(record) })
    }
    val additions = buildList {
        softwareFeature.visitModelDefaults(
            AdditionRecordDefault::class.java,
            ModelDefault.Visitor<DataAdditionRecord> { record -> add(record) })
    }
    val nestedObjectAccess = buildList {
        softwareFeature.visitModelDefaults(
            NestedObjectAccessDefault::class.java,
            ModelDefault.Visitor<NestedObjectAccessRecord> { record -> add(record) })
    }
    return ModelDefaultsResolutionResults(featureName, assignments, additions, nestedObjectAccess)
}


internal
fun softwareFeatureRegistryBasedModelDefaultsRegistrar(softwareFeatureRegistry: SoftwareFeatureRegistry): ModelDefaultsDefinitionRegistrar = object : ModelDefaultsDefinitionRegistrar {
    override fun registerDefaults(modelDefaultsBySoftwareFeature: Map<String, ModelDefaultsResolutionResults>) {
        softwareFeatureRegistry.softwareFeatureImplementations.values.forEach { implementation ->
            modelDefaultsBySoftwareFeature[implementation.featureName]?.let { modelDefaults ->
                val recordsFromModelDefaults = modelDefaults.additions.map(::AdditionRecordDefault) +
                    modelDefaults.assignments.map(::AssignmentRecordDefault) +
                    modelDefaults.nestedObjectAccess.map(::NestedObjectAccessDefault)
                recordsFromModelDefaults.forEach(implementation::addModelDefault)
            }
        }
    }
}
