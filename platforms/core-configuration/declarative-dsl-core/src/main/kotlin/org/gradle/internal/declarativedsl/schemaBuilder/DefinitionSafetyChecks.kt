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

import com.google.common.graph.Graph
import com.google.common.graph.GraphBuilder
import com.google.common.graph.Graphs
import com.google.common.graph.Traverser
import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.FunctionSemantics
import org.gradle.declarative.dsl.schema.ProjectFeatureOrigin
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.declarative.dsl.schema.UnsafeSchemaItem
import org.gradle.internal.declarativedsl.analysis.SchemaTypeRefContext
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaBuildingContextElement.TagContextElement

internal fun checkSafeTypeRequirements(
    schema: AnalysisSchema,
    host: SchemaBuildingHost
): List<SchemaResult.Failure> = buildList {
    val safeFeatureDefinitionsByDefinitionClass = schema.dataClassTypesByFqName.values
        .filterIsInstance<DataClass>()
        .filter { type -> type.metadata.any { it is ProjectFeatureOrigin && it.isSafeDefinition } }
        .associateWith { type -> type.metadata.filterIsInstance<ProjectFeatureOrigin>().filter { it.isSafeDefinition } }

    val reachabilityViaMembers = GraphBuilder.directed().allowsSelfLoops(true).immutable<DataClass>().apply {
        schema.dataClassTypesByFqName.values.forEach { schemaClass ->
            if (schemaClass is DataClass) {
                addNode(schemaClass)
                val usesClasses = usedClassesWithinFeature(schemaClass, schema)
                usesClasses.forEach {
                    putEdge(schemaClass, it)
                }
            }
        }
    }.build()

    val safelyUsedClasses = Traverser.forGraph(reachabilityViaMembers).breadthFirst(safeFeatureDefinitionsByDefinitionClass.keys)
        .filterNot { ignoreSafeUsageOfUnsafeClass(it.name.qualifiedName) }

    val usedIn = Graphs.transpose(reachabilityViaMembers)

    safelyUsedClasses.forEach { reportUnsafeDeclarationsInSafelyUsedClass(host, it, usedIn) }
}

internal fun ignoreSafeUsageOfUnsafeClass(classQualifiedName: String) = classQualifiedName.startsWith("org.gradle.api.")

private fun MutableList<SchemaResult.Failure>.reportUnsafeDeclarationsInSafelyUsedClass(
    host: SchemaBuildingHost,
    safelyUsedClass: DataClass,
    usedIn: Graph<DataClass>
) {
    val usedInSafeFeatures by lazy {
        Traverser.forGraph(usedIn).breadthFirst(safelyUsedClass).filterIsInstance<DataClass>().flatMap { dataClass ->
            dataClass.metadata.filterIsInstance<ProjectFeatureOrigin>().filter { it.isSafeDefinition }
        }
    }

    fun report(memberTag: TagContextElement?, unsafeApi: UnsafeSchemaItem) {
        host.withTag(SchemaBuildingTags.usedInSafeFeatures(usedInSafeFeatures.groupBy({ it.ecosystemPluginId }, { it.featureName }))) {
            host.withTag(SchemaBuildingTags.schemaClass(safelyUsedClass)) {
                val issue = SchemaBuildingIssue.UnsafeDeclarationInSafeFeatureApi(usedInSafeFeatures, unsafeApi)
                if (memberTag != null) {
                    host.withTag(memberTag) { add(host.schemaBuildingFailure(issue)) }
                } else {
                    add(host.schemaBuildingFailure(issue))
                }
            }
        }
    }

    safelyUsedClass.metadata.filterIsInstance<UnsafeSchemaItem>().forEach { unsafeApi ->
        report(null, unsafeApi)
    }
    safelyUsedClass.properties.forEach {
        it.metadata.filterIsInstance<UnsafeSchemaItem>().forEach { unsafeApi ->
            report(SchemaBuildingTags.schemaProperty(it), unsafeApi)
        }
    }
    safelyUsedClass.memberFunctions.forEach { function ->
        function.metadata.filterIsInstance<UnsafeSchemaItem>().forEach { unsafeApi ->
            report(SchemaBuildingTags.schemaFunction(function), unsafeApi)
        }
    }
}

private fun usedClassesWithinFeature(dataClass: DataClass, schema: AnalysisSchema): Iterable<DataClass> = buildSet {
    val typeRefs = SchemaTypeRefContext(schema)
    dataClass.properties.forEach { property ->
        (typeRefs.resolveRef(property.valueType) as? DataClass)?.let(::add)
    }
    dataClass.memberFunctions.forEach { function ->
        if (isFeatureFunction(function)) {
            // We are collecting classes "used" by specific features, so if a function applies another feature, we don't follow it.
            return@forEach
        }

        // Just looking at `DataClass`es (but not parameterized types) is enough for now: generic types cannot have members and are just shallow containers, cannot be unsafe
        function.parameters.forEach { parameter ->
            (typeRefs.resolveRef(parameter.type) as? DataClass)?.let(::add)
        }
        (typeRefs.resolveRef(function.returnValueType) as? DataClass)?.let(::add)
        (function.semantics as? FunctionSemantics.ConfigureSemantics)?.let { semantics ->
            (typeRefs.resolveRef(semantics.configuredType) as? DataClass)?.let(::add)
        }
    }
}

private fun isFeatureFunction(function: SchemaMemberFunction) =
    function.metadata.any { it is ProjectFeatureOrigin }
