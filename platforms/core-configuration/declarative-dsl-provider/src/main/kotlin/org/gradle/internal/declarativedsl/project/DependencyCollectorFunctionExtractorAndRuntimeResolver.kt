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

package org.gradle.internal.declarativedsl.project

import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.Dependencies
import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.internal.declarativedsl.analysis.DataConstructor
import org.gradle.internal.declarativedsl.analysis.DataMemberFunction
import org.gradle.internal.declarativedsl.analysis.DataParameter
import org.gradle.internal.declarativedsl.analysis.DataTopLevelFunction
import org.gradle.internal.declarativedsl.analysis.FunctionSemantics
import org.gradle.internal.declarativedsl.analysis.FunctionSemantics.ConfigureSemantics.ConfigureBlockRequirement.NOT_ALLOWED
import org.gradle.internal.declarativedsl.analysis.ParameterValueBinding
import org.gradle.internal.declarativedsl.analysis.SchemaMemberFunction
import org.gradle.internal.declarativedsl.mappingToJvm.DeclarativeRuntimeFunction
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeFunctionResolver
import org.gradle.internal.declarativedsl.schemaBuilder.DataSchemaBuilder
import org.gradle.internal.declarativedsl.schemaBuilder.FunctionExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.toDataTypeRef
import java.lang.reflect.Type
import java.util.Locale
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.functions
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.isSuperclassOf
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.javaType


class DependencyCollectorFunctionExtractorAndRuntimeResolver(private val gavDependencyParam: DataParameter, private val projectDependencyParam: DataParameter) : FunctionExtractor, RuntimeFunctionResolver {
    /**
     * Map from a class -> { map from function name -> list of parameter types } for all functions that are extracted by this type,
     * and can later be resolved at runtime.
     */
    private
    val managedFunctions: MutableMap<KClass<*>, Map<DataMemberFunction, DeclarativeRuntimeFunction>> = mutableMapOf()

    override fun memberFunctions(kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<SchemaMemberFunction> {
        val discoveredCollectorNames = kClass.memberFunctions
            .filter { function -> hasDependencyCollectorGetterSignature(kClass, function) }
            .map { function -> dependencyCollectorNameFromGetterName(function.name) }

        val managedFunctionsForKClass = mutableMapOf<DataMemberFunction, DeclarativeRuntimeFunction>()
        discoveredCollectorNames.forEach { name ->
            listOf(gavDependencyParam, projectDependencyParam).forEach { param ->
                managedFunctionsForKClass[buildDataMemberFunction(kClass, name, param)] = buildDeclarativeRuntimeFunction(name)
            }
        }
        managedFunctions[kClass] = managedFunctionsForKClass

        return managedFunctionsForKClass.keys
    }

    override fun constructors(kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<DataConstructor> = emptyList()
    override fun topLevelFunction(function: KFunction<*>, preIndex: DataSchemaBuilder.PreIndex): DataTopLevelFunction? = null

    private
    fun dependencyCollectorNameFromGetterName(getterName: String) = getterName.removePrefix("get").replaceFirstChar { it.lowercase(Locale.getDefault()) }

    private
    fun dependencyCollectorGetterNameFromName(name: String) = "get${name.replaceFirstChar { it.uppercase(Locale.getDefault()) }}"

    private
    fun buildDataMemberFunction(kClass: KClass<*>, name: String, dependencyParam: DataParameter) = DataMemberFunction(
        kClass.toDataTypeRef(),
        name,
        listOf(dependencyParam),
        false,
        FunctionSemantics.AddAndConfigure(ProjectDependency::class.toDataTypeRef(), NOT_ALLOWED)
    )

    private
    fun buildDeclarativeRuntimeFunction(name: String) = object : DeclarativeRuntimeFunction {
        override fun callBy(receiver: Any, binding: Map<DataParameter, Any?>, hasLambda: Boolean): DeclarativeRuntimeFunction.InvocationResult {
            val dependencyCollector = getDependencyCollector(receiver, name)
            when (val dependencyNotation = binding.values.single()) {
                is ProjectDependency -> dependencyCollector.add(dependencyNotation)
                else -> dependencyCollector.add(dependencyNotation.toString())
            }
            return DeclarativeRuntimeFunction.InvocationResult(Unit, null)
        }
    }

    private
    fun getDependencyCollector(receiver: Any, name: String) = receiver::class.functions
        .first { function -> function.name == dependencyCollectorGetterNameFromName(name) }
        .call(receiver) as DependencyCollector

    @OptIn(ExperimentalStdlibApi::class) // For javaType
    private
    fun hasDependencyCollectorGetterSignature(receiverClass: KClass<*>, function: KFunction<*>): Boolean {
        if (!receiverClass.isSubclassOf(Dependencies::class)) {
            return false
        }
        val returnType: Type = try {
            function.returnType.javaType
        } catch (e: Throwable) { // Sometimes reflection fails with an error when the return type is unusual, if it failed then it's not a getter of interest
            Void::class.java
        }
        return function.name.startsWith("get") && returnType == DependencyCollector::class.java && function.parameters.size == 1
    }

    override fun resolve(receiverClass: KClass<*>, name: String, parameterValueBinding: ParameterValueBinding): RuntimeFunctionResolver.Resolution {
        // We can't just use find receiverClass directly as a key because at runtime we get a decorated class with a different type
        // that extends the original class we extracted into the managedFunctions map, so we have to check the superClass
        return managedFunctions.entries.find { it.key.isSuperclassOf(receiverClass) }
            ?.value // Map<DataMemberFunction, DeclarativeRuntimeFunction>?
            ?.entries?.find { it.key.simpleName == name && it.key.parameters == parameterValueBinding.bindingMap.keys.toList() }
            ?.value // DeclarativeRuntimeFunction?
            ?.run { RuntimeFunctionResolver.Resolution.Resolved(this) }
            ?: RuntimeFunctionResolver.Resolution.Unresolved
    }
}
