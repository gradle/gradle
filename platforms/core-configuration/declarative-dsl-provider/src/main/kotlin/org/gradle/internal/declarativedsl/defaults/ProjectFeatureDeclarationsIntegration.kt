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
import org.gradle.plugin.software.internal.ProjectFeatureImplementation
import org.gradle.plugin.software.internal.ModelDefault
import org.gradle.plugin.software.internal.ProjectFeatureDeclarations


internal
fun projectFeatureRegistryBasedModelDefaultsRepository(projectFeatureDeclarations: ProjectFeatureDeclarations): ModelDefaultsRepository = object : ModelDefaultsRepository {
    val projectFeatureImplementationsById = projectFeatureDeclarations.projectFeatureImplementations.values.flatten().associateBy { it.uniqueId }

    override fun findDefaults(featureId: String): ModelDefaultsResolutionResults? =
        projectFeatureImplementationsById[featureId]?.let { projectFeature ->
            resolutionResultsFromDefaultsFor(projectFeature.uniqueId, projectFeature)
        }
}


private
fun resolutionResultsFromDefaultsFor(featureId: String, projectFeature: ProjectFeatureImplementation<*, *>): ModelDefaultsResolutionResults {
    val assignments = buildList {
        projectFeature.visitModelDefaults(
            AssignmentRecordDefault::class.java,
            ModelDefault.Visitor<AssignmentRecord> { record -> add(record) })
    }
    val additions = buildList {
        projectFeature.visitModelDefaults(
            AdditionRecordDefault::class.java,
            ModelDefault.Visitor<DataAdditionRecord> { record -> add(record) })
    }
    val nestedObjectAccess = buildList {
        projectFeature.visitModelDefaults(
            NestedObjectAccessDefault::class.java,
            ModelDefault.Visitor<NestedObjectAccessRecord> { record -> add(record) })
    }
    return ModelDefaultsResolutionResults(featureId, assignments, additions, nestedObjectAccess)
}


internal
fun projectFeatureRegistryBasedModelDefaultsRegistrar(projectFeatureDeclarations: ProjectFeatureDeclarations): ModelDefaultsDefinitionRegistrar = object : ModelDefaultsDefinitionRegistrar {
    override fun registerDefaults(modelDefaultsByProjectFeatureId: Map<String, ModelDefaultsResolutionResults>) {
        projectFeatureDeclarations.projectFeatureImplementations.values.flatten().forEach { implementation ->
            modelDefaultsByProjectFeatureId[implementation.uniqueId]?.let { modelDefaults ->
                val recordsFromModelDefaults = modelDefaults.additions.map(::AdditionRecordDefault) +
                    modelDefaults.assignments.map(::AssignmentRecordDefault) +
                    modelDefaults.nestedObjectAccess.map(::NestedObjectAccessDefault)
                recordsFromModelDefaults.forEach(implementation::addModelDefault)
            }
        }
    }
}
