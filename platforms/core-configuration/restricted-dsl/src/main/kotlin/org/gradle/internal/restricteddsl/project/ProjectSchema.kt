/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.restricteddsl.project

import com.h0tk3y.kotlin.staticObjectNotation.Adding
import com.h0tk3y.kotlin.staticObjectNotation.analysis.AnalysisSchema
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataParameter
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataTypeRef
import com.h0tk3y.kotlin.staticObjectNotation.analysis.FqName
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ParameterValueBinding
import com.h0tk3y.kotlin.staticObjectNotation.mappingToJvm.RestrictedReflectionToObjectConverter
import com.h0tk3y.kotlin.staticObjectNotation.mappingToJvm.RestrictedRuntimeFunction
import com.h0tk3y.kotlin.staticObjectNotation.mappingToJvm.RestrictedRuntimeProperty
import com.h0tk3y.kotlin.staticObjectNotation.mappingToJvm.RuntimeFunctionResolver
import com.h0tk3y.kotlin.staticObjectNotation.mappingToJvm.RuntimePropertyResolver
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.ObjectReflection
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.CollectedPropertyInformation
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.CompositeDataClassSchemaProducer
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.DataClassSchemaProducer
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.defaultDataClassSchemaProducer
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.kotlinFunctionAsConfigureLambda
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.plus
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.schemaFromTypes
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.treatInterfaceAsConfigureLambda
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerFactory
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.restricteddsl.evaluationSchema.EvaluationSchema
import org.gradle.internal.restricteddsl.evaluationSchema.EvaluationStep
import org.gradle.internal.restricteddsl.plugins.PluginDependencySpecWithProperties
import org.gradle.internal.restricteddsl.plugins.PluginsCollectingPluginsBlock
import org.gradle.internal.restricteddsl.plugins.RestrictedPluginDependenciesSpecScope
import org.gradle.plugin.management.internal.DefaultPluginRequest
import org.gradle.plugin.management.internal.PluginRequests
import org.gradle.plugin.use.internal.PluginRequestApplicator
import kotlin.reflect.KClass
import kotlin.reflect.full.createType
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.isSubclassOf


internal
fun projectEvaluationSchema(targetScope: ClassLoaderScope, scriptSource: ScriptSource) = EvaluationSchema(
    createAnalysisSchemaForProject(targetScope),
    additionalPropertyResolvers = listOf(hardcodeProjectExtensionsProperties),
    additionalFunctionResolvers = listOf(hardcodeDependencyExtensionFunctions),
    evaluationSteps = listOf(applyPluginsOnlyEvaluationStep(targetScope, scriptSource), applyEverythingExceptForPluginsEvaluationStep)
)


private
fun createAnalysisSchemaForProject(targetScope: ClassLoaderScope): AnalysisSchema {
    val projectAccessors = projectAccessorsSupport(targetScope)

    return schemaFromTypes(
        ProjectTopLevelReceiver::class,
        listOf(
            ProjectTopLevelReceiver::class,
            // Plugins:
            RestrictedPluginDependenciesSpecScope::class,
            PluginDependencySpecWithProperties::class,
            // Dependencies:
            RestrictedDependenciesHandler::class,
            ProjectDependency::class
        ) + projectAccessors.typesFromExtensions,
        dataClassSchemaProducer = CompositeDataClassSchemaProducer(
            listOf(defaultDataClassSchemaProducer, DependencyDslAccessorsProducer()) + projectAccessors.dataClassSchemaProducers
        ),

        configureLambdas = treatInterfaceAsConfigureLambda(Action::class).plus(kotlinFunctionAsConfigureLambda)
    )
}


private
class ProjectAccessorsSupport(val typesFromExtensions: List<KClass<*>>, val dataClassSchemaProducers: List<DataClassSchemaProducer>)


private
fun projectAccessorsSupport(targetScope: ClassLoaderScope): ProjectAccessorsSupport {
    val projectAccessorsClass = try {
        targetScope.localClassLoader.loadClass("org.gradle.accessors.dm.RootProjectAccessor").kotlin
    } catch (e: ClassNotFoundException) {
        return ProjectAccessorsSupport(emptyList(), emptyList())
    }

    val projectAccessorsExtension = CollectedPropertyInformation(
        "projects",
        projectAccessorsClass.createType(),
        returnType = DataTypeRef.Name(FqName.parse(projectAccessorsClass.qualifiedName!!)),
        isReadOnly = true,
        hasDefaultValue = true
    )

    val producer = ExtensionPropertiesProducer(mapOf(ProjectTopLevelReceiver::class to listOf(projectAccessorsExtension)))

    return ProjectAccessorsSupport(
        typesFromExtensions = listOf(projectAccessorsClass),
        dataClassSchemaProducers = listOf(producer)
    )
}


private
val hardcodeProjectExtensionsProperties = object : RuntimePropertyResolver {
    override fun resolvePropertyRead(receiverClass: KClass<*>, name: String): RuntimePropertyResolver.Resolution {
        if (receiverClass.isSubclassOf(Project::class) && name == "projects") {
            return RuntimePropertyResolver.Resolution.Resolved(object : RestrictedRuntimeProperty {
                override fun getValue(receiver: Any) = (receiver as Project).extensions.getByName("projects")
                override fun setValue(receiver: Any, value: Any?): Unit = throw UnsupportedOperationException()
            })
        }
        val pluginsDslExtensionName = ProjectTopLevelReceiver::pluginsDsl.name
        if (receiverClass.isSubclassOf(Project::class) && name == pluginsDslExtensionName) {
            return RuntimePropertyResolver.Resolution.Resolved(object : RestrictedRuntimeProperty {
                override fun getValue(receiver: Any): Any {
                    receiver as Project
                    if (receiver.extensions.findByName(projectPluginsDslExtensionKey) == null)
                        receiver.extensions.add(projectPluginsDslExtensionKey, PluginsCollectingPluginsBlock())
                    return receiver.extensions.getByName(projectPluginsDslExtensionKey)
                }

                override fun setValue(receiver: Any, value: Any?) = throw UnsupportedOperationException()
            })
        }
        return RuntimePropertyResolver.Resolution.Unresolved
    }

    override fun resolvePropertyWrite(receiverClass: KClass<*>, name: String): RuntimePropertyResolver.Resolution = RuntimePropertyResolver.Resolution.Unresolved
}


private
val hardcodeDependencyExtensionFunctions = object : RuntimeFunctionResolver {
    val names = RestrictedDependenciesHandler::class.declaredMemberFunctions.filter { it.annotations.any { it is Adding } }.map { it.name }

    override fun resolve(receiverClass: KClass<*>, name: String, parameterValueBinding: ParameterValueBinding): RuntimeFunctionResolver.Resolution {
        if (receiverClass.isSubclassOf(DependencyHandler::class) && name in names && parameterValueBinding.bindingMap.size == 1) {
            return RuntimeFunctionResolver.Resolution.Resolved(object : RestrictedRuntimeFunction {
                override fun callBy(receiver: Any, binding: Map<DataParameter, Any?>): Any {
                    (receiver as DependencyHandler).add(name, binding.values.single() ?: error("null value in dependency DSL"))
                    return Unit
                }
            })
        }
        return RuntimeFunctionResolver.Resolution.Unresolved
    }
}


private
fun applyPluginsOnlyEvaluationStep(targetScope: ClassLoaderScope, scriptSource: ScriptSource) = object : EvaluationStep {
    override fun apply(target: Any, topLevelObjectReflection: ObjectReflection, converter: RestrictedReflectionToObjectConverter) {
        converter.apply(topLevelObjectReflection, topLevelPluginsConversionFilter(isPlugins = true))

        applyPluginsToProject(target as ProjectInternal)
    }

    private
    fun applyPluginsToProject(target: ProjectInternal) {
        val plugins = target.extensions.findByName(projectPluginsDslExtensionKey) as? PluginsCollectingPluginsBlock
        if (plugins != null) {
            val pluginRequests = plugins.specs.map { DefaultPluginRequest(it.id, it.version, it.apply, 0, scriptSource.displayName) }
            val scriptHandler = target.services.get(ScriptHandlerFactory::class.java).create(scriptSource, targetScope)
            target.services.get(PluginRequestApplicator::class.java)
                .applyPlugins(PluginRequests.of(pluginRequests), scriptHandler, target.pluginManager, targetScope)
        }
    }
}


private
val applyEverythingExceptForPluginsEvaluationStep = object : EvaluationStep {
    override fun apply(target: Any, topLevelObjectReflection: ObjectReflection, converter: RestrictedReflectionToObjectConverter) {
        converter.apply(topLevelObjectReflection, topLevelPluginsConversionFilter(isPlugins = false))
    }
}


private
val projectPluginsDslExtensionName = ProjectTopLevelReceiver::pluginsDsl.name


private
val projectPluginsDslExtensionKey = "__$projectPluginsDslExtensionName"


private
fun topLevelPluginsConversionFilter(isPlugins: Boolean) =
    RestrictedReflectionToObjectConverter.ConversionFilter { obj ->
        if ((obj.type.kClass == ProjectTopLevelReceiver::class)) {
            obj.properties.keys.filter { (it.name == projectPluginsDslExtensionName) == isPlugins }
        } else obj.properties.keys
    }
