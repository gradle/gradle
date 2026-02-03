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

package org.gradle.internal.declarativedsl.dependencycollectors

import com.google.common.graph.Traverser
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.Dependencies
import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.api.plugins.jvm.PlatformDependencyModifiers
import org.gradle.declarative.dsl.schema.DataMemberFunction
import org.gradle.declarative.dsl.schema.DataParameter
import org.gradle.declarative.dsl.schema.DataTopLevelFunction
import org.gradle.declarative.dsl.schema.SchemaFunction
import org.gradle.internal.declarativedsl.InstanceAndPublicType
import org.gradle.internal.declarativedsl.analysis.DefaultDataMemberFunction
import org.gradle.internal.declarativedsl.analysis.FunctionSemanticsInternal
import org.gradle.internal.declarativedsl.mappingToJvm.DeclarativeRuntimeFunction
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeFunctionResolver
import org.gradle.internal.declarativedsl.schemaBuilder.DataSchemaBuilder
import org.gradle.internal.declarativedsl.schemaBuilder.ExtractionResult
import org.gradle.internal.declarativedsl.schemaBuilder.FunctionExtractionMetadata
import org.gradle.internal.declarativedsl.schemaBuilder.FunctionExtractionResult
import org.gradle.internal.declarativedsl.schemaBuilder.FunctionExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.LossySchemaBuildingOperation
import org.gradle.internal.declarativedsl.schemaBuilder.MemberKind
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaBuildingContextElement
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaBuildingHost
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaResult
import org.gradle.internal.declarativedsl.schemaBuilder.SupportedCallable
import org.gradle.internal.declarativedsl.schemaBuilder.isJavaBeanGetter
import org.gradle.internal.declarativedsl.schemaBuilder.orError
import org.gradle.internal.declarativedsl.schemaBuilder.orFailWith
import org.gradle.internal.declarativedsl.schemaBuilder.schemaResult
import org.gradle.internal.declarativedsl.schemaBuilder.withTag
import java.util.Locale
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.isSubclassOf

internal
class DependencyCollectorFunctionExtractorAndRuntimeResolver(
    private val gavDependencyParam: (SchemaBuildingHost) -> DataParameter,
    private val dependencyParam: (SchemaBuildingHost) -> DataParameter,
) : FunctionExtractor, RuntimeFunctionResolver {

    private
    val collectorDeclarationsByClass: MutableMap<KClass<*>, Map<DataMemberFunction, DeclarativeRuntimeFunction>> = mutableMapOf()
    val modifierDeclarationsByClass: MutableMap<KClass<*>, Map<DataMemberFunction, DeclarativeRuntimeFunction>> = mutableMapOf()

    private
    data class DependencyCollectorDeclaration(
        val originalDeclaration: SupportedCallable,
        val addingSchemaFunction: SchemaResult<DataMemberFunction>,
        val runtimeFunction: DeclarativeRuntimeFunction,
    )

    private
    fun expandToOverloads(host: SchemaBuildingHost, produceDeclaration: (DataParameter) -> DependencyCollectorDeclaration) =
        listOf(gavDependencyParam, dependencyParam).map {
            val param = it(host)
            produceDeclaration(param)
        }

    override fun memberFunctions(host: SchemaBuildingHost, kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): List<FunctionExtractionResult> = buildList {
        // Early out and skip this logic if the class is not a subclass of Dependencies, as other types shouldn't have any dependency collectors
        if (kClass.isSubclassOf(Dependencies::class)) {
            addAll(extractCollectorSchemaFunctions(host, kClass))

            // Only add platform modifiers if this Dependencies subtype is also a subtype of PlatformDependencyModifiers, these aren't needed otherwise
            if (kClass.isSubclassOf(PlatformDependencyModifiers::class)) {
                addAll(extractModifierSchemaFunctions(host, kClass).map { ExtractionResult.Companion.of(it, FunctionExtractionMetadata(emptyList())) })
            }
        }
    }

    private
    fun extractModifierSchemaFunctions(host: SchemaBuildingHost, kClass: KClass<*>): Set<SchemaResult<DataMemberFunction>> {
        val modifiersBySchemaFunction: Map<SchemaResult<DataMemberFunction>, DeclarativeRuntimeFunction> = mapOf(
            buildDataMemberFunction(host, kClass, "platform", gavDependencyParam(host)) to PlatformRuntimeFunction,
            buildDataMemberFunction(host, kClass, "platform", dependencyParam(host)) to PlatformRuntimeFunction
        )
        @OptIn(LossySchemaBuildingOperation::class) // No user input here, it should not fail, and if it does, it's our fault
        modifierDeclarationsByClass[kClass] = modifiersBySchemaFunction.filterKeys { it is SchemaResult.Result }.mapKeys { it.key.orError() }
        return modifiersBySchemaFunction.keys
    }

    private
    fun extractCollectorSchemaFunctions(host: SchemaBuildingHost, kClass: KClass<*>): List<FunctionExtractionResult> {
        val discoveredCollectorDeclarations: List<DependencyCollectorDeclaration> = run {
            val members = host.classMembers(kClass).declarativeMembers

            val namedCollectorGetters = members
                .filter { it.kind == MemberKind.FUNCTION && isDependencyCollectorPropertyOrGetter(kClass, it) }
                .map { function -> dependencyCollectorNameFromGetterName(function.name) to function }

            val namedCollectorProperties = members
                .filter { it.kind == MemberKind.READ_ONLY_PROPERTY && isDependencyCollectorPropertyOrGetter(kClass, it) }
                .map { property -> property.name to property }

            (namedCollectorGetters + namedCollectorProperties)
                .flatMap { (name, callable) ->
                    expandToOverloads(host) { param -> DependencyCollectorDeclaration(callable, buildDataMemberFunction(host, kClass, name, param), buildDeclarativeRuntimeFunction(callable)) }
                }
        }

        val declarationsBySchemaFunctions = discoveredCollectorDeclarations.filter { it.addingSchemaFunction is SchemaResult.Result }
            .associate { (it.addingSchemaFunction as SchemaResult.Result).result to it.runtimeFunction }

        if (!declarationsBySchemaFunctions.isEmpty()) {
            collectorDeclarationsByClass[kClass] = declarationsBySchemaFunctions
        }
        return discoveredCollectorDeclarations.map { ExtractionResult.Companion.of(it.addingSchemaFunction, FunctionExtractionMetadata(listOf(it.originalDeclaration))) }
    }

    private
    object PlatformRuntimeFunction : DeclarativeRuntimeFunction {
        override fun callBy(
            receiver: Any?,
            binding: Map<DataParameter, Any?>,
            hasLambda: Boolean
        ): DeclarativeRuntimeFunction.InvocationResult {
            val platform = (receiver as PlatformDependencyModifiers).platform
            val modifiedDependency = binding.values.single().let { arg ->
                when (arg) {
                    is CharSequence -> platform.modify(arg)
                    is ModuleDependency -> platform.modify(arg)
                    else -> error("Unsupported argument type: ${arg!!.javaClass} shouldn't be possible")
                }
            }
            val result = InstanceAndPublicType(modifiedDependency, modifiedDependency.javaClass::class)
            return DeclarativeRuntimeFunction.InvocationResult(result, result)
        }
    }

    override fun topLevelFunction(host: SchemaBuildingHost, function: KFunction<*>, preIndex: DataSchemaBuilder.PreIndex): SchemaResult<DataTopLevelFunction>? = null

    private
    fun dependencyCollectorNameFromGetterName(getterName: String) = getterName.removePrefix("get").replaceFirstChar { it.lowercase(Locale.getDefault()) }

    private
    fun buildDataMemberFunction(host: SchemaBuildingHost, kClass: KClass<*>, name: String, dependencyParam: DataParameter): SchemaResult<DataMemberFunction> =
        host.withTag(dependencyCollectorTag(kClass, name)) {
            DefaultDataMemberFunction(
                host.containerTypeRef(kClass)
                    .orFailWith { return it },
                name,
                listOf(dependencyParam),
                false,
                FunctionSemanticsInternal.DefaultAddAndConfigure(
                    @OptIn(LossySchemaBuildingOperation::class) // referencing a predefined type is safe
                    host.containerTypeRef(ProjectDependency::class).orError(),
                    FunctionSemanticsInternal.DefaultConfigureBlockRequirement.DefaultNotAllowed
                )
            ).let(::schemaResult)
        }

    private fun dependencyCollectorTag(kClass: KClass<*>, name: String) =
        SchemaBuildingContextElement.TagContextElement("dependency collector '$name' in $kClass")

    private
    fun buildDeclarativeRuntimeFunction(collectorAccessor: SupportedCallable) = object : DeclarativeRuntimeFunction {
        override fun callBy(receiver: Any?, binding: Map<DataParameter, Any?>, hasLambda: Boolean): DeclarativeRuntimeFunction.InvocationResult {
            val dependencyCollector = collectorAccessor.kCallable.call(checkNotNull(receiver)) as DependencyCollector
            when (val dependencyNotation = binding.values.single()) {
                is ProjectDependency -> dependencyCollector.add(dependencyNotation)
                else -> dependencyCollector.add(dependencyNotation.toString())
            }
            return DeclarativeRuntimeFunction.InvocationResult(InstanceAndPublicType.Companion.UNIT, InstanceAndPublicType.Companion.NULL)
        }
    }

    companion object {
        fun isDependencyCollectorPropertyOrGetter(owner: KClass<*>, callable: SupportedCallable): Boolean =
            owner.isSubclassOf(Dependencies::class) &&
                callable.returnType.classifier == DependencyCollector::class &&
                (callable.kind == MemberKind.FUNCTION && callable.isJavaBeanGetter ||
                    callable.kind == MemberKind.READ_ONLY_PROPERTY)
    }

    override fun resolve(receiverClass: KClass<*>, schemaFunction: SchemaFunction, scopeClassLoader: ClassLoader): RuntimeFunctionResolver.Resolution {
        // We can't just use find receiverClass directly as a key because at runtime we get a decorated class with a different type
        // that extends the original class we extracted into the managedFunctions map, so we have to check the superClass
        fun searchDeclarationsByClass(declarationsByClass: MutableMap<KClass<*>, Map<DataMemberFunction, DeclarativeRuntimeFunction>>): RuntimeFunctionResolver.Resolution.Resolved? = typeHierarchyViaJavaReflection(receiverClass)
            .firstNotNullOfOrNull(declarationsByClass::get)
            ?.entries?.find { (fn, _) -> fn == schemaFunction }
            ?.value?.let(RuntimeFunctionResolver.Resolution::Resolved)

        return searchDeclarationsByClass(collectorDeclarationsByClass) ?: searchDeclarationsByClass(modifierDeclarationsByClass) ?: RuntimeFunctionResolver.Resolution.Unresolved
    }

    /**
     * Gradle decoration does not generate correct Kotlin metadata for decorated Kotlin types.
     * Because of that, decorated types do not appear as subtypes of the original types when inspected with Kotlin reflection.
     * Workaround: use Java reflection to determine the supertypes.
     * TODO: Either fix Kotlin metadata in decorated classes or introduce generic utilities
     */
    private
    fun typeHierarchyViaJavaReflection(kClass: KClass<*>): Iterable<KClass<*>> =
        Traverser.forGraph<Class<*>> { listOfNotNull(it.superclass) + it.interfaces }
            .breadthFirst(kClass.java).map { it.kotlin }
}
