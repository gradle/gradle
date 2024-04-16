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

import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.declarative.dsl.schema.DataConstructor
import org.gradle.declarative.dsl.schema.DataParameter
import org.gradle.declarative.dsl.schema.DataTopLevelFunction
import org.gradle.declarative.dsl.schema.FunctionSemantics.ConfigureBlockRequirement.NOT_ALLOWED
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.internal.declarativedsl.analysis.DataMemberFunction
import org.gradle.internal.declarativedsl.analysis.DefaultDataParameter
import org.gradle.internal.declarativedsl.analysis.FunctionSemanticsInternal
import org.gradle.internal.declarativedsl.analysis.ParameterSemanticsInternal
import org.gradle.internal.declarativedsl.analysis.ParameterValueBinding
import org.gradle.internal.declarativedsl.evaluationSchema.EvaluationSchemaComponent
import org.gradle.internal.declarativedsl.mappingToJvm.DeclarativeRuntimeFunction
import org.gradle.internal.declarativedsl.mappingToJvm.RuntimeFunctionResolver
import org.gradle.internal.declarativedsl.schemaBuilder.DataSchemaBuilder
import org.gradle.internal.declarativedsl.schemaBuilder.FunctionExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.toDataTypeRef
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.isSubclassOf


/**
 * Introduces functions for registering project dependencies, such as `implementation(...)`, as member functions of:
 * * [RestrictedDependenciesHandler] in the schema,
 * * [DependencyHandler] when resolved at runtime.
 * * Any type with getters returning [DependencyCollector] in the schema.
 *
 * Inspects the configurations available in the given project to build the functions.
 */
internal
class DependencyConfigurationsComponent(
    project: Project,
) : EvaluationSchemaComponent {
    private
    val configurations = DependencyConfigurations(project.configurations.names.toList())

    private
    val gavDependencyParam = DefaultDataParameter("dependency", String::class.toDataTypeRef(), false, ParameterSemanticsInternal.Unknown)

    private
    val projectDependencyParam = DefaultDataParameter("dependency", ProjectDependency::class.toDataTypeRef(), false, ParameterSemanticsInternal.Unknown)

    private
    val dependencyCollectorFunctionExtractorAndRuntimeResolver = DependencyCollectorFunctionExtractorAndRuntimeResolver(gavDependencyParam, projectDependencyParam)

    override fun functionExtractors(): List<FunctionExtractor> = listOf(
        DependencyFunctionsExtractor(configurations, projectDependencyParam),
        dependencyCollectorFunctionExtractorAndRuntimeResolver
    )

    override fun runtimeFunctionResolvers(): List<RuntimeFunctionResolver> = listOf(
        RuntimeDependencyFunctionResolver(configurations),
        dependencyCollectorFunctionExtractorAndRuntimeResolver
    )
}


private
class DependencyConfigurations(
    val configurationNames: List<String>
)


private
class DependencyFunctionsExtractor(private val configurations: DependencyConfigurations, private val projectDependencyParam: DataParameter) : FunctionExtractor {
    override fun memberFunctions(kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<SchemaMemberFunction> =
        if (kClass == RestrictedDependenciesHandler::class) {
            configurations.configurationNames.map { configurationName ->
                DataMemberFunction(
                    kClass.toDataTypeRef(),
                    configurationName,
                    listOf(projectDependencyParam),
                    false,
                    FunctionSemanticsInternal.AddAndConfigure(ProjectDependency::class.toDataTypeRef(), NOT_ALLOWED)
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
            return RuntimeFunctionResolver.Resolution.Resolved(object : DeclarativeRuntimeFunction {
                override fun callBy(receiver: Any, binding: Map<DataParameter, Any?>, hasLambda: Boolean): DeclarativeRuntimeFunction.InvocationResult {
                    (receiver as DependencyHandler).add(name, binding.values.single() ?: error("null value in dependency DSL"))
                    return DeclarativeRuntimeFunction.InvocationResult(Unit, null)
                }
            })
        }

        return RuntimeFunctionResolver.Resolution.Unresolved
    }
}
