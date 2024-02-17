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
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.isSubclassOf


internal
class DependencyConfigurationSchemaComponents(
    val functionExtractor: FunctionExtractor,
    val runtimeFunctionResolver: RuntimeFunctionResolver
)


internal
fun dependencyConfigurationSchemaComponents(project: Project): DependencyConfigurationSchemaComponents {
    val configurations = DependencyConfigurations(project.configurations.names.toList())

    return DependencyConfigurationSchemaComponents(
        DependencyFunctionsExtractor(configurations),
        RuntimeDependencyFunctionResolver(configurations)
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
                    FunctionSemantics.AddAndConfigure(ProjectDependency::class.toDataTypeRef(), FunctionSemantics.AddAndConfigure.ConfigureBlockRequirement.NOT_ALLOWED)
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
                override fun callBy(receiver: Any, binding: Map<DataParameter, Any?>): RestrictedRuntimeFunction.InvocationResult {
                    (receiver as DependencyHandler).add(name, binding.values.single() ?: error("null value in dependency DSL"))
                    return RestrictedRuntimeFunction.InvocationResult(Unit, null)
                }
            })
        }
        return RuntimeFunctionResolver.Resolution.Unresolved
    }
}
