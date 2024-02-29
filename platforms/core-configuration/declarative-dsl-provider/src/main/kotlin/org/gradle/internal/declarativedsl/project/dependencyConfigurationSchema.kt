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

import org.gradle.api.Action
import org.gradle.internal.declarativedsl.analysis.DataConstructor
import org.gradle.internal.declarativedsl.analysis.DataMemberFunction
import org.gradle.internal.declarativedsl.analysis.DataParameter
import org.gradle.internal.declarativedsl.analysis.DataTopLevelFunction
import org.gradle.internal.declarativedsl.analysis.FunctionSemantics
import org.gradle.internal.declarativedsl.analysis.ParameterSemantics
import org.gradle.internal.declarativedsl.analysis.ParameterValueBinding
import org.gradle.internal.declarativedsl.analysis.SchemaMemberFunction
import org.gradle.internal.declarativedsl.mappingToJvm.RestrictedRuntimeFunction
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeFunctionResolver
import org.gradle.internal.declarativedsl.schemaBuilder.DataSchemaBuilder
import org.gradle.internal.declarativedsl.schemaBuilder.FunctionExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.toDataTypeRef
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.internal.declarativedsl.analysis.FunctionSemantics.ConfigureSemantics.ConfigureBlockRequirement.NOT_ALLOWED
import org.gradle.internal.declarativedsl.analysis.ref
import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchemaComponent
import org.gradle.internal.declarativedsl.language.DataType
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.isSubclassOf


/**
 * Introduces functions for registering project dependencies, such as `implementation(...)`, as member functions of:
 * * [RestrictedDependenciesHandler] in the schema,
 * * [DependencyHandler] when resolved at runtime.
 * * [RestrictedLibraryDependencies] in the schema.
 *
 * Inspects the configurations available in the given project to build the functions.
 */
internal
class DependencyConfigurationsComponent(
    project: Project,
) : EvaluationSchemaComponent {
    private
    val configurations = DependencyConfigurations(project.configurations.names.toList())

    override fun functionExtractors(): List<FunctionExtractor> = listOf(
        DependencyFunctionsExtractor(configurations),
        DependencyCollectorFunctionsExtractor(configurations)
    )

    override fun runtimeFunctionResolvers(): List<RuntimeFunctionResolver> = listOf(
        RuntimeDependencyFunctionResolver(configurations),
        RuntimeDependencyCollectorFunctionResolver(configurations)
    )
}


private
class DependencyConfigurations(
    val configurationNames: List<String>
)


private
class DependencyFunctionsExtractor(val configurations: DependencyConfigurations) : FunctionExtractor {
    override fun memberFunctions(kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<SchemaMemberFunction> =
        if (kClass == RestrictedDependenciesHandler::class) {
            configurations.configurationNames.map { configurationName ->
                DataMemberFunction(
                    kClass.toDataTypeRef(),
                    configurationName,
                    listOf(DataParameter("dependency", ProjectDependency::class.toDataTypeRef(), false, ParameterSemantics.Unknown)),
                    false,
                    FunctionSemantics.AddAndConfigure(ProjectDependency::class.toDataTypeRef(), NOT_ALLOWED)
                )
            }
        } else emptyList()

    override fun constructors(kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<DataConstructor> = emptyList()
    override fun topLevelFunction(function: KFunction<*>, preIndex: DataSchemaBuilder.PreIndex): DataTopLevelFunction? = null
}

private
class DependencyCollectorFunctionsExtractor(val configurations: DependencyConfigurations) : FunctionExtractor {
    override fun memberFunctions(kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<SchemaMemberFunction> =
        if (kClass == RestrictedLibraryDependencies::class) {
            configurations.configurationNames.map { configurationName ->
                DataMemberFunction(
                    kClass.toDataTypeRef(),
                    configurationName,
                    listOf(DataParameter("dependency", String::class.toDataTypeRef(), false, ParameterSemantics.Unknown)),
                    false,
                    FunctionSemantics.AddAndConfigure(RestrictedLibraryDependencies::class.toDataTypeRef(), FunctionSemantics.ConfigureSemantics.ConfigureBlockRequirement.NOT_ALLOWED)
                )
            }
        } else emptyList()

    override fun constructors(kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<DataConstructor> = emptyList()
    override fun topLevelFunction(function: KFunction<*>, preIndex: DataSchemaBuilder.PreIndex): DataTopLevelFunction? = null
}

private
class RuntimeDependencyFunctionResolver(configurations: DependencyConfigurations) : RuntimeFunctionResolver {
    private
    val nameSet = configurations.configurationNames.toSet()

    override fun resolve(receiverClass: KClass<*>, name: String, parameterValueBinding: ParameterValueBinding): RuntimeFunctionResolver.Resolution {
        if (receiverClass.isSubclassOf(DependencyHandler::class) && name in nameSet && parameterValueBinding.bindingMap.size == 1) {
            return RuntimeFunctionResolver.Resolution.Resolved(object : RestrictedRuntimeFunction {
                override fun callBy(receiver: Any, binding: Map<DataParameter, Any?>, hasLambda: Boolean): RestrictedRuntimeFunction.InvocationResult {
                    (receiver as DependencyHandler).add(name, binding.values.single() ?: error("null value in dependency DSL"))
                    return RestrictedRuntimeFunction.InvocationResult(Unit, null)
                }
            })
        }
        return RuntimeFunctionResolver.Resolution.Unresolved
    }
}

private
class RuntimeDependencyCollectorFunctionResolver(configurations: DependencyConfigurations) : RuntimeFunctionResolver {
    private
    val nameSet = configurations.configurationNames.toSet()

    override fun resolve(receiverClass: KClass<*>, name: String, parameterValueBinding: ParameterValueBinding): RuntimeFunctionResolver.Resolution {
        // TODO: The runtime decoration isn't working as expected here
        // When receiverClass == class org.gradle.internal.declarativedsl.project.RestrictedLibraryDependencies_Decorated
        // Then receiverClass.isSubclassOf(RestrictedLibraryDependencies::class) == false
        // So, I'll just test the name
        if (receiverClass.simpleName == "RestrictedLibraryDependencies_Decorated" && name in nameSet && parameterValueBinding.bindingMap.size == 1) {
            return RuntimeFunctionResolver.Resolution.Resolved(object : RestrictedRuntimeFunction {
                override fun callBy(receiver: Any, binding: Map<DataParameter, Any?>, hasLambda: Boolean): RestrictedRuntimeFunction.InvocationResult {
                    val libraryDependencies = (receiver as RestrictedLibraryDependencies)
                    val dependencyNotation = binding.values.single().toString()
                    when (name) {
                        "api" -> libraryDependencies.getApi().add(dependencyNotation) {}
                        "implementation" -> libraryDependencies.getImplementation().add(dependencyNotation) {}
                        else -> error("Unknown configuration: $name for dependency: $dependencyNotation")
                    }
                    return RestrictedRuntimeFunction.InvocationResult(Unit, null)
                }
            })
        }
        return RuntimeFunctionResolver.Resolution.Unresolved
    }
}
