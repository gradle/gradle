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

import org.gradle.internal.declarativedsl.evaluator.conventions.ConventionDefinitionRegistrar
import org.gradle.internal.declarativedsl.evaluator.conventions.SoftwareTypeConventionRepository
import org.gradle.internal.declarativedsl.evaluator.conventions.SoftwareTypeConventionResolutionResults
import org.gradle.plugin.software.internal.Convention
import org.gradle.plugin.software.internal.SoftwareTypeRegistry


internal
fun softwareTypeRegistryBasedConventionRepository(softwareTypeRegistry: SoftwareTypeRegistry): SoftwareTypeConventionRepository = object : SoftwareTypeConventionRepository {
    override fun findConventions(softwareTypeName: String): SoftwareTypeConventionResolutionResults? =
        softwareTypeRegistry.softwareTypeImplementations[softwareTypeName]?.let { softwareType ->
            conventionResolutionResultsFrom(softwareTypeName, softwareType.conventions)
        }
}


/**
 * This registry is used by {@link NonDeclarativeConventionHandler} to avoid applying declarative conventions when applying
 * plugins from a declarative context (i.e. the declarative script processing will handle applying the conventions).  In other words, this
 * registry will only return declarative conventions when a software type plugin is applied from an imperative context (i.e. a non-declarative script).
 */
internal
fun softwareTypeRegistryBasedConventionRepositoryWithContext(softwareTypeRegistry: SoftwareTypeRegistry): SoftwareTypeConventionRepository = object : SoftwareTypeConventionRepository {
    override fun findConventions(softwareTypeName: String): SoftwareTypeConventionResolutionResults? =
        softwareTypeRegistry.softwareTypeImplementations[softwareTypeName]?.let { softwareType ->
            conventionResolutionResultsFrom(softwareTypeName, softwareType.conventionsForCurrentContext)
        }
}


private
fun conventionResolutionResultsFrom(softwareTypeName: String, conventions: List<Convention<*>>): SoftwareTypeConventionResolutionResults {
    val assignments = buildList {
        conventions.filterIsInstance<AssignmentRecordConvention>().forEach { it.apply(::add) }
    }
    val additions = buildList {
        conventions.filterIsInstance<AdditionRecordConvention>().forEach { it.apply(::add) }
    }
    val nestedObjectAccess = buildList {
        conventions.filterIsInstance<NestedObjectAccessConvention>().forEach { it.apply(::add) }
    }
    return SoftwareTypeConventionResolutionResults(softwareTypeName, assignments, additions, nestedObjectAccess)
}


internal
fun softwareTypeRegistryBasedConventionRegistrar(softwareTypeRegistry: SoftwareTypeRegistry): ConventionDefinitionRegistrar = object : ConventionDefinitionRegistrar {
    override fun registerConventions(conventionsBySoftwareType: Map<String, SoftwareTypeConventionResolutionResults>) {
        softwareTypeRegistry.softwareTypeImplementations.values.forEach { softwareTypeImplementation ->
            conventionsBySoftwareType[softwareTypeImplementation.softwareType]?.let { conventions ->
                val conventionRecords = conventions.additions.map(::AdditionRecordConvention) +
                    conventions.assignments.map(::AssignmentRecordConvention) +
                    conventions.nestedObjectAccess.map(::NestedObjectAccessConvention)
                conventionRecords.forEach(softwareTypeImplementation::addConvention)
            }
        }
    }
}
