/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.ntcscript

import groovy.lang.GroovyObject
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.configuration.ScriptPlugin
import org.gradle.configuration.ScriptPluginFactory
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.plugin.management.internal.PluginRequests
import org.gradle.plugin.management.internal.autoapply.AutoAppliedPluginHandler
import org.gradle.plugin.use.internal.PluginRequestApplicator
import org.gradle.plugin.use.internal.PluginRequestCollector
import javax.inject.Inject


class NtcScriptPluginFactory @Inject constructor(
    private val autoAppliedPluginHandler: AutoAppliedPluginHandler,
    private val pluginRequestApplicator: PluginRequestApplicator
) : ScriptPluginFactory {

    override fun create(
        scriptSource: ScriptSource,
        scriptHandler: ScriptHandler,
        targetScope: ClassLoaderScope,
        baseScope: ClassLoaderScope,
        topLevelScript: Boolean
    ): ScriptPlugin = object : ScriptPlugin {

        override fun apply(target: Any) {
            require(target is ProjectInternal && topLevelScript) {
                "Only project scripts are supported."
            }
            applyTo(target, scriptSource, scriptHandler, targetScope)
        }

        override fun getSource(): ScriptSource = scriptSource
    }

    private
    fun applyTo(
        target: ProjectInternal,
        scriptSource: ScriptSource,
        scriptHandler: ScriptHandler,
        targetScope: ClassLoaderScope
    ) {
        val model = readModelFrom(scriptSource)

        val initialPluginRequests = collectPluginRequests(model, scriptSource)

        applyPlugins(initialPluginRequests, target, scriptHandler, targetScope)

        applyModelTo(target, model)
    }

    private
    fun applyModelTo(target: ProjectInternal, model: BuildScriptModel) {
        model.extensions.forEach { extensionModel ->
            val extension = target.extensions.getByName(extensionModel.name)
            require(extension is GroovyObject)
            extensionModel.properties.forEach { property ->
                extension.setProperty(property.key, valueOf(property.value))
            }
        }

        model.dependencies.forEach { (configurationName, deps) ->
            target.dependencies.run {
                deps.forEach { dep ->
                    when (dep) {
                        is BuildScriptModel.Dependency.ExternalDependency ->
                            add(
                                configurationName,
                                mapOf("group" to dep.group, "name" to dep.module, "version" to dep.version)
                            )
                        is BuildScriptModel.Dependency.PlatformDependency -> {
                            add(
                                configurationName,
                                platform(mapOf("group" to dep.group, "name" to dep.module, "version" to dep.version))
                            )
                        }
                        is BuildScriptModel.Dependency.ProjectDependency -> TODO("project")
                    }
                }
            }
        }
    }

    private
    fun collectPluginRequests(model: BuildScriptModel, scriptSource: ScriptSource): PluginRequests =
        PluginRequestCollector(scriptSource).run {
            model.plugins.forEach { plugin ->
                createPluginRequestFor(plugin)
            }
            pluginRequests
        }

    private
    fun PluginRequestCollector.createPluginRequestFor(plugin: BuildScriptModel.Plugin) {
        createSpec(plugin.position?.line ?: 1)
            .id(plugin.id)
            .also { spec -> plugin.version?.let(spec::version) }
    }

    private
    fun readModelFrom(scriptSource: ScriptSource) =
        NtcScriptReader().readToml(scriptSource.resource.text)

    private
    fun valueOf(value: BuildScriptModel.PropertyValue) = when (value) {
        is BuildScriptModel.PropertyValue.StringValue -> value.value
        is BuildScriptModel.PropertyValue.DoubleValue -> TODO()
        is BuildScriptModel.PropertyValue.IntValue -> TODO()
        is BuildScriptModel.PropertyValue.RecordValue -> TODO()
    }

    private
    fun applyPlugins(initialPluginRequests: PluginRequests, target: ProjectInternal, scriptHandler: ScriptHandler, targetScope: ClassLoaderScope) {
        pluginRequestApplicator.applyPlugins(
            autoAppliedPluginHandler.mergeWithAutoAppliedPlugins(initialPluginRequests, target),
            scriptHandler as ScriptHandlerInternal,
            target.pluginManager,
            targetScope
        )
    }
}
