/*
 * Copyright 2016 the original author or authors.
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
package gradlebuild.basics.tasks

import gradlebuild.basics.util.ReproduciblePropertiesWriter
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.file.RegularFileProperty

import java.util.Properties


@CacheableTask
abstract class ClasspathManifest : DefaultTask() {

    @get:Input
    abstract val optionalProjects: ListProperty<String>

    @get:Internal
    abstract val runtimeClasspath: ConfigurableFileCollection

    @Input
    val runtime = externalDependencies.elements.map { it.map { it.asFile.name }.sorted() }

    @get:Internal
    abstract val externalDependencies: ConfigurableFileCollection

    @Input
    val projects = runtimeClasspath.elements.map { it.mapNotNull { it.toGradleModuleName() }.sorted() }

    @get:OutputFile
    abstract val manifestFile: RegularFileProperty

    @TaskAction
    fun generate() {
        ReproduciblePropertiesWriter.store(createProperties(), manifestFile.get().asFile)
    }

    private
    fun createProperties() = Properties().also { properties ->
        properties["runtime"] = runtime.get().joinToString(",")
        properties["projects"] = projects.get().joinToString(",")
        optionalProjects.get().takeIf { it.isNotEmpty() }?.let { optional ->
            properties["optional"] = optional.joinForProperties()
        }
    }

    private
    fun FileSystemLocation.toGradleModuleName(): String? = asFile.name
        .takeIf { it.startsWith("gradle-") || it.contains("-patched-for-gradle-") }
        ?.run { substring(0, lastIndexOf('-')) }

    private
    fun Iterable<String>.joinForProperties() = sorted().joinToString(",")
}
