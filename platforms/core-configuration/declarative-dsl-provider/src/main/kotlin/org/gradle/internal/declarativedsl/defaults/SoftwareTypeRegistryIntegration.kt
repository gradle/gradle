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
import org.gradle.plugin.software.internal.ModelDefault
import org.gradle.plugin.software.internal.SoftwareTypeImplementation
import org.gradle.plugin.software.internal.SoftwareTypeRegistry


internal
fun softwareTypeRegistryBasedModelDefaultsRepository(softwareTypeRegistry: SoftwareTypeRegistry): ModelDefaultsRepository = object : ModelDefaultsRepository {
    override fun findDefaults(softwareTypeName: String): ModelDefaultsResolutionResults? =
        softwareTypeRegistry.softwareTypeImplementations[softwareTypeName]?.let { softwareType ->
            resolutionResultsFromDefaultsFor(softwareTypeName, softwareType)
        }
}


private
fun resolutionResultsFromDefaultsFor(softwareTypeName: String, softwareType: SoftwareTypeImplementation<*>): ModelDefaultsResolutionResults {
    val assignments = buildList {
        softwareType.visitModelDefaults(
            AssignmentRecordDefault::class.java,
            ModelDefault.Visitor<AssignmentRecord> { record -> add(record) })
    }
    val additions = buildList {
        softwareType.visitModelDefaults(
            AdditionRecordDefault::class.java,
            ModelDefault.Visitor<DataAdditionRecord> { record -> add(record) })
    }
    val nestedObjectAccess = buildList {
        softwareType.visitModelDefaults(
            NestedObjectAccessDefault::class.java,
            ModelDefault.Visitor<NestedObjectAccessRecord> { record -> add(record) })
    }
    return ModelDefaultsResolutionResults(softwareTypeName, assignments, additions, nestedObjectAccess)
}


internal
fun softwareTypeRegistryBasedModelDefaultsRegistrar(softwareTypeRegistry: SoftwareTypeRegistry): ModelDefaultsDefinitionRegistrar = object : ModelDefaultsDefinitionRegistrar {
    override fun registerDefaults(modelDefaultsBySoftwareType: Map<String, ModelDefaultsResolutionResults>) {
        softwareTypeRegistry.softwareTypeImplementations.values.forEach { softwareTypeImplementation ->
            modelDefaultsBySoftwareType[softwareTypeImplementation.softwareType]?.let { modelDefaults ->
                val recordsFromModelDefaults = modelDefaults.additions.map(::AdditionRecordDefault) +
                    modelDefaults.assignments.map(::AssignmentRecordDefault) +
                    modelDefaults.nestedObjectAccess.map(::NestedObjectAccessDefault)
                recordsFromModelDefaults.forEach(softwareTypeImplementation::addModelDefault)
            }
        }
    }
}
