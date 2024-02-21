/*
 * Copyright 2020 the original author or authors.
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
package gradlebuild.packaging.tasks

import gradlebuild.basics.util.ReproduciblePropertiesWriter
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import org.gradle.api.file.RegularFileProperty

import java.util.Properties


@CacheableTask
@Suppress("unused")
abstract class PluginsManifest : DefaultTask() {

    @get:Internal
    abstract val coreClasspath: ConfigurableFileCollection

    @Input
    val core = coreClasspath.toGradleModuleNameProvider()

    @get:Internal
    abstract val pluginsClasspath: ConfigurableFileCollection

    @Input
    val plugins = pluginsClasspath.toGradleModuleNameProvider()

    @get:OutputFile
    abstract val manifestFile: RegularFileProperty

    @TaskAction
    fun generate() {
        ReproduciblePropertiesWriter.store(createProperties(), manifestFile.get().asFile)
    }

    private
    fun createProperties() = Properties().also { properties ->
        properties["plugins"] = (plugins.get() - core.get()).joinForProperties()
    }

    private
    fun FileCollection.toGradleModuleNameProvider() = elements.map { it.mapNotNull { it.toGradleModuleName() }.sorted() }

    private
    fun FileSystemLocation.toGradleModuleName(): String? = asFile.name
        .takeIf { it.startsWith("gradle-") }
        ?.run { substring(0, lastIndexOf('-')) }

    private
    fun Iterable<String>.joinForProperties() = sorted().joinToString(",")
}
