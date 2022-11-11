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
package org.gradle.plugin.devel.plugins

import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileTree
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.initialization.ScriptHandlerInternal
import org.gradle.api.internal.plugins.PluginRegistry
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.TaskProvider
import org.gradle.plugin.devel.tasks.ValidatePlugins
import org.gradle.plugin.use.PluginId
import java.io.File
import java.net.URISyntaxException
import java.net.URL
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collectors
import javax.annotation.Nonnull
import javax.inject.Inject

/**
 * Validates plugins applied to a build, by checking property annotations on work items like tasks and artifact transforms.
 * This plugin is similar to [ValidatePlugins] but instead of checking the plugins *written in* the current build,
 * it checks the plugins *applied to* the current build.
 *
 * See the user guide for more information on
 * [incremental build](https://docs.gradle.org/current/userguide/more_about_tasks.html#sec:up_to_date_checks) and
 * [caching task outputs](https://docs.gradle.org/current/userguide/build_cache.html#sec:task_output_caching).
 */
@Incubating
@CacheableTask
abstract class ExternalPluginValidationPlugin @Inject constructor(private val fileOperations: FileOperations) : Plugin<Project> {
    override fun apply(@Nonnull project: Project) {
        val lifecycleTask = createLifecycleTask(project)
        val registry = findPluginRegistry(project)
        val jarsByPlugin: MutableMap<String, MutableList<File>> = HashMap()
        project.plugins.configureEach { p -> configurePluginValidation(project, lifecycleTask, registry, jarsByPlugin, p) }
    }

    private fun configurePluginValidation(project: Project,
                                          lifecycleTask: TaskProvider<Task>,
                                          registry: PluginRegistry,
                                          jarsByPlugin: MutableMap<String, MutableList<File>>,
                                          plugin: Plugin<*>) {
        val pluginClass: Class<*> = plugin.javaClass
        if (isExternal(pluginClass)) {
            val pluginForClass = registry.findPluginForClass(pluginClass)
            val pluginId: String = pluginForClass.map(Function { obj: PluginId -> obj.id }).orElseGet(Supplier { computePluginName(plugin) })
            val pluginJar = findPluginJar(pluginClass)
            if (pluginJar != null) {
                jarsByPlugin.computeIfAbsent(pluginId, Function<String, MutableList<File>> { firstSeenPlugin: String -> registerValidationTaskForNewPlugin(firstSeenPlugin, project, lifecycleTask) })
                        .add(pluginJar)
            } else {
                LOGGER.warn("Validation won't be performed for plugin '{}' because we couldn't locate its jar file", pluginId)
            }
        }
    }

    private fun registerValidationTaskForNewPlugin(pluginId: String, project: Project, lifecycleTask: TaskProvider<Task>): List<File> {
        val jarsForPlugin: List<File> = ArrayList()
        val validationTask = configureValidationTask(project, jarsForPlugin, pluginId)
        lifecycleTask.configure { task: Task -> task.dependsOn(validationTask) }
        return jarsForPlugin
    }

    private fun configureValidationTask(project: Project,
                                        pluginJars: List<File>,
                                        pluginId: String): TaskProvider<ValidatePlugins> {
        val idWithoutDots = pluginId.replace('.', '_')
        return project.tasks.register(VALIDATE_EXTERNAL_PLUGIN_TASK_PREFIX + idWithoutDots, ValidatePlugins::class.java, { task: ValidatePlugins ->
            task.setGroup(JavaGradlePluginPlugin.PLUGIN_DEVELOPMENT_GROUP)
            task.setDescription(JavaGradlePluginPlugin.VALIDATE_PLUGIN_TASK_DESCRIPTION)
            task.outputFile.set(project.layout.buildDirectory.file("reports/plugins/validation-report-for-$idWithoutDots.txt"))
            val scriptHandler = project.buildscript as ScriptHandlerInternal
            val scriptClassPath = scriptHandler.scriptClassPath.asFiles
            task.classes.setFrom(pluginClassesOf(pluginJars))
            task.classpath.setFrom(scriptClassPath)
        })
    }

    private fun pluginClassesOf(pluginJars: List<File>): List<FileTree> {
        return pluginJars.stream()
                .map(Function { zipPath: File? -> fileOperations.zipTree(zipPath!!) })
                .collect(Collectors.toList())
    }

    companion object {
        private val LOGGER = Logging.getLogger(ExternalPluginValidationPlugin::class.java)
        private const val VALIDATE_EXTERNAL_PLUGINS_TASK_NAME = "validateExternalPlugins"
        private const val VALIDATE_EXTERNAL_PLUGIN_TASK_PREFIX = "validatePluginWithId_"

        /**
         * Generates a plugin name for a plugin which doesn't have an id.
         * @param plugin the plugin class
         * @return an id that will be used for generating task names and for reporting
         */
        private fun computePluginName(plugin: Plugin<*>): String {
            return plugin.javaClass.name
        }

        private fun findPluginRegistry(project: Project): PluginRegistry {
            return (project as ProjectInternal).services.get(PluginRegistry::class.java)
        }

        private fun createLifecycleTask(project: Project): TaskProvider<Task> {
            return project.tasks.register(VALIDATE_EXTERNAL_PLUGINS_TASK_NAME)
        }

        private fun findPluginJar(pluginClass: Class<*>): File? {
            return toFile(pluginClass.protectionDomain.codeSource.location)
        }

        private fun isExternal(pluginClass: Class<*>): Boolean {
            return !pluginClass.name.startsWith("org.gradle")
        }

        private fun toFile(url: URL): File? {
            return try {
                File(url.toURI())
            } catch (e: URISyntaxException) {
                null
            }
        }
    }
}
