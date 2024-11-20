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
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.Dependencies
import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.declarative.dsl.schema.DataConstructor
import org.gradle.declarative.dsl.schema.DataMemberFunction
import org.gradle.declarative.dsl.schema.DataParameter
import org.gradle.declarative.dsl.schema.DataTopLevelFunction
import org.gradle.declarative.dsl.schema.SchemaFunction
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.internal.declarativedsl.analysis.DefaultDataMemberFunction
import org.gradle.internal.declarativedsl.analysis.FunctionSemanticsInternal
import org.gradle.internal.declarativedsl.mappingToJvm.DeclarativeRuntimeFunction
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeFunctionResolver
import org.gradle.internal.declarativedsl.mappingToJvm.nullInstanceAndPublicType
import org.gradle.internal.declarativedsl.schemaBuilder.DataSchemaBuilder
import org.gradle.internal.declarativedsl.schemaBuilder.FunctionExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.toDataTypeRef
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
    private val gavDependencyParam: DataParameter,
    private val projectDependencyParam: DataParameter
) : FunctionExtractor, RuntimeFunctionResolver {

    private
    val collectorDeclarationsByClass: MutableMap<KClass<*>, Map<DataMemberFunction, DeclarativeRuntimeFunction>> = mutableMapOf()

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
    fun expandToOverloads(produceDeclaration: (DataParameter) -> DependencyCollectorDeclaration) =
        listOf(gavDependencyParam, projectDependencyParam).map(produceDeclaration)

    override fun memberFunctions(kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<SchemaMemberFunction> {
        val discoveredCollectorDeclarations: List<DependencyCollectorDeclaration> = kClass.memberFunctions
            .filter { function -> hasDependencyCollectorGetterSignature(kClass, function) }
            .flatMap { function ->
                val name = dependencyCollectorNameFromGetterName(function.name)
                expandToOverloads { param ->
                    DependencyCollectorDeclaration(
                        buildDataMemberFunction(kClass, name, param),
                        buildDeclarativeRuntimeFunction(DependencyCollectorAccessor.Getter(function)),
                    )
                }
            }
            .plus(kClass.memberProperties.filter { isDependencyCollectorProperty(kClass, it) }.flatMap { property ->
                expandToOverloads { param ->
                    DependencyCollectorDeclaration(
                        buildDataMemberFunction(kClass, property.name, param),
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

    override fun constructors(kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<DataConstructor> = emptyList()
    override fun topLevelFunction(function: KFunction<*>, preIndex: DataSchemaBuilder.PreIndex): DataTopLevelFunction? = null

    private
    fun dependencyCollectorNameFromGetterName(getterName: String) = getterName.removePrefix("get").replaceFirstChar { it.lowercase(Locale.getDefault()) }

    private
    fun buildDataMemberFunction(kClass: KClass<*>, name: String, dependencyParam: DataParameter) = DefaultDataMemberFunction(
        kClass.toDataTypeRef(),
        name,
        listOf(dependencyParam),
        false,
        FunctionSemanticsInternal.DefaultAddAndConfigure(ProjectDependency::class.toDataTypeRef(), FunctionSemanticsInternal.DefaultConfigureBlockRequirement.DefaultNotAllowed)
    )

    private
    fun buildDeclarativeRuntimeFunction(collectorAccessor: DependencyCollectorAccessor) = object : DeclarativeRuntimeFunction {
        override fun callBy(receiver: Any, binding: Map<DataParameter, Any?>, hasLambda: Boolean): DeclarativeRuntimeFunction.InvocationResult {
            val dependencyCollector = collectorAccessor(receiver)
            when (val dependencyNotation = binding.values.single()) {
                is ProjectDependency -> dependencyCollector.add(dependencyNotation)
                else -> dependencyCollector.add(dependencyNotation.toString())
            }
            return DeclarativeRuntimeFunction.InvocationResult(Unit to Unit::class, nullInstanceAndPublicType)
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

    override fun resolve(receiverClass: KClass<*>, schemaFunction: SchemaFunction): RuntimeFunctionResolver.Resolution {
        // We can't just use find receiverClass directly as a key because at runtime we get a decorated class with a different type
        // that extends the original class we extracted into the managedFunctions map, so we have to check the superClass
        return typeHierarchyViaJavaReflection(receiverClass)
            .firstNotNullOfOrNull(collectorDeclarationsByClass::get)
            ?.entries?.find { (fn, _) -> fn == schemaFunction }
            ?.value?.let(RuntimeFunctionResolver.Resolution::Resolved)
            ?: RuntimeFunctionResolver.Resolution.Unresolved
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
