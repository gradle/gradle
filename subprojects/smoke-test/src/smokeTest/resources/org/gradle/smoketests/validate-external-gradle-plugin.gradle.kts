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

import org.gradle.api.file.ArchiveOperations
import org.gradle.api.internal.initialization.ScriptHandlerInternal
import org.gradle.api.internal.plugins.PluginRegistry
import org.gradle.api.internal.project.ProjectInternal
import java.net.URISyntaxException
import java.net.URL
import java.util.stream.Collectors

/**
 * Validates external plugins applied to a build, by checking property annotations
 * on work items like tasks and artifact transforms.
 * This is similar to [ValidatePlugins] but instead of checking the plugins *written in* the current build,
 * it checks the plugins *applied to* the current build.
 */
gradle.beforeProject {
    val lifecycleTask = project.tasks.register("validateExternalPlugins")
    project.plugins.configureEach { configurePluginValidation(project, lifecycleTask, javaClass) }
}

fun configurePluginValidation(
    project: Project,
    lifecycleTask: TaskProvider<Task>,
    pluginClass: Class<*>
) {
    val jarsByPlugin = mutableMapOf<String, MutableList<File>>()
    if (isExternal(pluginClass)) {
        val registry = findPluginRegistry(project)
        val pluginId: String = registry.findPluginForClass(pluginClass)
            .map(PluginId::getId)
            .orElseGet { pluginClass.name }
        val pluginJar = findPluginJar(pluginClass)
        if (pluginJar != null) {
            jarsByPlugin.computeIfAbsent(pluginId) { firstSeenPlugin -> registerValidationTaskForNewPlugin(firstSeenPlugin, project, lifecycleTask) }
                .add(pluginJar)
        } else {
            Logging.getLogger(javaClass).warn("Validation won't be performed for plugin '{}' because we couldn't locate its jar file", pluginId)
        }
    }
}

fun registerValidationTaskForNewPlugin(pluginId: String, project: Project, lifecycleTask: TaskProvider<Task>): MutableList<File> {
    val jarsForPlugin = mutableListOf<File>()
    val validationTask = configureValidationTask(project, jarsForPlugin, pluginId)
    lifecycleTask.configure { dependsOn(validationTask) }
    return jarsForPlugin
}

fun configureValidationTask(project: Project,
                            pluginJars: MutableList<File>,
                            pluginId: String): TaskProvider<ValidatePlugins> {
    val idWithoutDots = pluginId.replace('.', '_')
    return project.tasks.register<ValidatePlugins>("validatePluginWithId_" + idWithoutDots) {
        group = "Plugin development"
        outputFile.set(project.layout.buildDirectory.file("reports/plugins/validation-report-for-$idWithoutDots.txt"))

        val scriptHandler = project.buildscript as ScriptHandlerInternal
        val scriptClassPath = scriptHandler.scriptClassPath.asFiles
        classpath.setFrom(scriptClassPath)

        val archiveOperations = findArchiveOperations(project)
        val pluginClassesOf = pluginJars.stream()
            .map { zipPath: File? -> archiveOperations.zipTree(zipPath!!) }
            .collect(Collectors.toList())
        classes.setFrom(pluginClassesOf)
    }
}

fun findPluginRegistry(project: Project) =
    (project as ProjectInternal).services.get(PluginRegistry::class.java)

fun findArchiveOperations(project: Project) =
    (project as ProjectInternal).services.get<ArchiveOperations>(ArchiveOperations::class.java)

fun findPluginJar(pluginClass: Class<*>) =
    toFile(pluginClass.protectionDomain.codeSource.location)

fun isExternal(pluginClass: Class<*>) =
    !pluginClass.name.startsWith("org.gradle")

fun toFile(url: URL): File? {
    return try {
        File(url.toURI())
    } catch (e: URISyntaxException) {
        null
    }
}
