/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.declarativedsl.schemaBuilder

import com.google.common.graph.GraphBuilder
import com.google.common.graph.MutableGraph
import com.google.common.graph.Traverser
import org.gradle.declarative.dsl.schema.FqName
import org.gradle.internal.declarativedsl.analysis.DefaultFqName
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery.DiscoveredClass.DiscoveryTag.FromClassDiscoveryTag
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery.DiscoveredClass.DiscoveryTag.ProjectFeatureDefinition
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery.DiscoveredClass.DiscoveryTag.Special

/**
 * Filters out schema building failures reported in the types that are only used in other types having at least one failure.
 *
 * @return A list of failures only in the types that are used from the correct types
 *   with an additional [org.gradle.declarative.dsl.evaluation.SchemaIssue.MoreFailuresInErroneousTypes] failure aggregating
 *   the failures filtered out, if any.
 */
fun pruneSchemaBuildingFailures(
    host: SchemaBuildingHost,
    allTypeDiscoveries: Iterable<TypeDiscovery.DiscoveredClass>,
    allFailures: Map<FqName, Iterable<SchemaResult.Failure>>
): List<SchemaResult.Failure> {
    if (allFailures.isEmpty())
        return emptyList()

    val usageFromCorrectTypesGraph = getUsageFromCorrectTypesGraph(allTypeDiscoveries, allFailures)

    val reachabilitySource = allTypeDiscoveries.filter { it.isReachabilitySource() }
        .mapTo(mutableSetOf(DefaultFqName.of(host.topLevelReceiverClass))) { DefaultFqName.of(it.kClass) }

    val typesReachableViaCorrectTypes = Traverser.forGraph(usageFromCorrectTypesGraph).breadthFirst(reachabilitySource).toSet()

    val otherErroneousTypeNames = allFailures.keys - typesReachableViaCorrectTypes

    return allFailures.filterKeys { it in typesReachableViaCorrectTypes }.values.flatten() +
        if (otherErroneousTypeNames.isNotEmpty()) {
            aggregateOmittedFailures(host, otherErroneousTypeNames, allFailures, typesReachableViaCorrectTypes)
        } else emptyList()
}

private fun getUsageFromCorrectTypesGraph(
    allTypeDiscoveries: Iterable<TypeDiscovery.DiscoveredClass>,
    allFailures: Map<FqName, Iterable<SchemaResult.Failure>>
): MutableGraph<FqName> = GraphBuilder.directed().build<FqName>().apply {
    /**
     * A feature definition type is never _accidentally_ added into a schema, so we still want error reports for all types directly referenced from the feature definition.
     */
    val featureDefinitions = allTypeDiscoveries.filter { it.discoveryTag is ProjectFeatureDefinition }.mapTo(mutableSetOf()) { DefaultFqName.of(it.kClass) }

    allTypeDiscoveries.forEach { typeDiscovery ->
        val to = DefaultFqName.of(typeDiscovery.kClass)
            .also(::addNode)

        if (typeDiscovery.discoveryTag is FromClassDiscoveryTag) {
            val from = DefaultFqName.of(typeDiscovery.discoveryTag.fromClass)
                .also(::addNode)
            if ((from in featureDefinitions || from !in allFailures) && to != from) {
                putEdge(from, to)
            }
        }
    }
}

private fun aggregateOmittedFailures(
    host: SchemaBuildingHost,
    otherErroneousTypeNames: Set<FqName>,
    allFailures: Map<FqName, Iterable<SchemaResult.Failure>>,
    typesReachableViaCorrectTypes: Set<FqName>
): List<SchemaResult.Failure> = host.inIsolatedContext {
    val otherErroneousTypeNameStrings = otherErroneousTypeNames.mapTo(mutableSetOf()) { it.qualifiedName }
    listOf(
        host.withTag(SchemaBuildingTags.erroneousTypes(otherErroneousTypeNameStrings)) {
            host.schemaBuildingFailure(
                SchemaBuildingIssue.MoreFailuresInErroneousTypes(
                    otherErroneousTypeNameStrings,
                    allFailures.filterKeys { it !in typesReachableViaCorrectTypes }.values.flatMap { it.map(SchemaResult.Failure::asReportableFailure) }
                )
            )
        }
    )
}

private fun TypeDiscovery.DiscoveredClass.isReachabilitySource() =
    when (discoveryTag) {
        is ProjectFeatureDefinition,
        is Special -> true

        else -> false
    }
