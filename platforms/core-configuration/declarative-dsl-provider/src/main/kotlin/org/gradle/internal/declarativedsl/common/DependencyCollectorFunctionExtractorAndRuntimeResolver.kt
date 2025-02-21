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

package org.gradle.internal.declarativedsl.common

import com.google.common.graph.Traverser
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.Dependencies
import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.api.plugins.jvm.PlatformDependencyModifiers
import org.gradle.declarative.dsl.schema.DataConstructor
import org.gradle.declarative.dsl.schema.DataMemberFunction
import org.gradle.declarative.dsl.schema.DataParameter
import org.gradle.declarative.dsl.schema.DataTopLevelFunction
import org.gradle.declarative.dsl.schema.SchemaFunction
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.internal.declarativedsl.InstanceAndPublicType
import org.gradle.internal.declarativedsl.analysis.DefaultDataMemberFunction
import org.gradle.internal.declarativedsl.analysis.FunctionSemanticsInternal
import org.gradle.internal.declarativedsl.mappingToJvm.DeclarativeRuntimeFunction
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeFunctionResolver
import org.gradle.internal.declarativedsl.schemaBuilder.DataSchemaBuilder
import org.gradle.internal.declarativedsl.schemaBuilder.FunctionExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaBuildingContextElement
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaBuildingHost
import org.gradle.internal.declarativedsl.schemaBuilder.withTag
import java.util.Locale
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.typeOf


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
        val addingSchemaFunction: DataMemberFunction,
        val runtimeFunction: DeclarativeRuntimeFunction,
    )

    private
    sealed interface DependencyCollectorAccessor : (Any) -> DependencyCollector {
        data class Getter(val getterFunction: KFunction<*>) : DependencyCollectorAccessor {
            override fun invoke(receiver: Any): DependencyCollector = getterFunction.call(receiver) as DependencyCollector
        }

        data class Property(val property: KProperty<*>) : DependencyCollectorAccessor {
            override fun invoke(receiver: Any): DependencyCollector = property.call(receiver) as DependencyCollector
        }
    }

    private
    fun expandToOverloads(host: SchemaBuildingHost, produceDeclaration: (DataParameter) -> DependencyCollectorDeclaration) =
        listOf(gavDependencyParam, dependencyParam).map {
            val param = it(host)
            produceDeclaration(param)
        }

    override fun memberFunctions(host: SchemaBuildingHost, kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<SchemaMemberFunction> {
        val result = mutableSetOf<DataMemberFunction>()

        // Early out and skip this logic if the class is not a subclass of Dependencies, as other types shouldn't have any dependency collectors
        if (kClass.isSubclassOf(Dependencies::class)) {
            result.addAll(extractCollectorSchemaFunctions(host, kClass))

            // Only add platform modifiers if this Dependencies subtype is also a subtype of PlatformDependencyModifiers, these aren't needed otherwise
            if (kClass.isSubclassOf(PlatformDependencyModifiers::class)) {
                result.addAll(extractModifierSchemaFunctions(host, kClass))
            }
        }

        return result
    }

    private
    fun extractModifierSchemaFunctions(host: SchemaBuildingHost, kClass: KClass<*>): Set<DataMemberFunction> {
        val modifiersBySchemaFunction: Map<DataMemberFunction, DeclarativeRuntimeFunction> = mapOf(
            buildDataMemberFunction(host, kClass, "platform", gavDependencyParam(host)) to PlatformRuntimeFunction,
            buildDataMemberFunction(host, kClass, "platform", dependencyParam(host)) to PlatformRuntimeFunction
        )
        modifierDeclarationsByClass[kClass] = modifiersBySchemaFunction
        return modifiersBySchemaFunction.keys
    }

    private
    fun extractCollectorSchemaFunctions(host: SchemaBuildingHost, kClass: KClass<*>): Set<DataMemberFunction> {
        val discoveredCollectorDeclarations: List<DependencyCollectorDeclaration> = kClass.memberFunctions
            .filter { function -> hasDependencyCollectorGetterSignature(kClass, function) }
            .flatMap { function ->
                val name = dependencyCollectorNameFromGetterName(function.name)
                expandToOverloads(host) { param ->
                    DependencyCollectorDeclaration(
                        buildDataMemberFunction(host, kClass, name, param),
                        buildDeclarativeRuntimeFunction(DependencyCollectorAccessor.Getter(function)),
                    )
                }
            }
            .plus(kClass.memberProperties.filter { isDependencyCollectorProperty(kClass, it) }.flatMap { property ->
                expandToOverloads(host) { param ->
                    DependencyCollectorDeclaration(
                        buildDataMemberFunction(host, kClass, property.name, param),
                        buildDeclarativeRuntimeFunction(DependencyCollectorAccessor.Property(property)),
                    )
                }
            })

        val declarationsBySchemaFunctions = discoveredCollectorDeclarations.associate { it.addingSchemaFunction to it.runtimeFunction }
        if (!declarationsBySchemaFunctions.isEmpty()) {
            collectorDeclarationsByClass[kClass] = declarationsBySchemaFunctions
        }
        return declarationsBySchemaFunctions.keys
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

    override fun constructors(host: SchemaBuildingHost, kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<DataConstructor> = emptyList()
    override fun topLevelFunction(host: SchemaBuildingHost, function: KFunction<*>, preIndex: DataSchemaBuilder.PreIndex): DataTopLevelFunction? = null

    private
    fun dependencyCollectorNameFromGetterName(getterName: String) = getterName.removePrefix("get").replaceFirstChar { it.lowercase(Locale.getDefault()) }

    private
    fun buildDataMemberFunction(host: SchemaBuildingHost, kClass: KClass<*>, name: String, dependencyParam: DataParameter) =
        host.withTag(dependencyCollectorTag(kClass, name)) {
            DefaultDataMemberFunction(
                host.containerTypeRef(kClass),
                name,
                listOf(dependencyParam),
                false,
                FunctionSemanticsInternal.DefaultAddAndConfigure(
                    host.containerTypeRef(ProjectDependency::class),
                    FunctionSemanticsInternal.DefaultConfigureBlockRequirement.DefaultNotAllowed
                )
            )
        }

    private fun dependencyCollectorTag(kClass: KClass<*>, name: String) =
        SchemaBuildingContextElement.TagContextElement("dependency collector '$name' in $kClass")

    private
    fun buildDeclarativeRuntimeFunction(collectorAccessor: DependencyCollectorAccessor) = object : DeclarativeRuntimeFunction {
        override fun callBy(receiver: Any?, binding: Map<DataParameter, Any?>, hasLambda: Boolean): DeclarativeRuntimeFunction.InvocationResult {
            val dependencyCollector = collectorAccessor(checkNotNull(receiver))
            when (val dependencyNotation = binding.values.single()) {
                is ProjectDependency -> dependencyCollector.add(dependencyNotation)
                else -> dependencyCollector.add(dependencyNotation.toString())
            }
            return DeclarativeRuntimeFunction.InvocationResult(InstanceAndPublicType.UNIT, InstanceAndPublicType.NULL)
        }
    }

    private
    fun hasDependencyCollectorGetterSignature(receiverClass: KClass<*>, function: KFunction<*>): Boolean {
        return receiverClass.isSubclassOf(Dependencies::class) && with(function) {
            name.startsWith("get") && returnType.classifier == DependencyCollector::class && parameters.size == 1
        }
    }

    private
    fun isDependencyCollectorProperty(receiverClass: KClass<*>, property: KProperty<*>): Boolean {
        return receiverClass.isSubclassOf(Dependencies::class) &&
            property.returnType == typeOf<DependencyCollector>() &&
            property !is KMutableProperty // TODO: decide what to do with `var foo: DependencyCollector`
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
