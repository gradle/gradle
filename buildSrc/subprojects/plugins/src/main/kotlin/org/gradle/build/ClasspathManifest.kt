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
package org.gradle.build

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.*

import java.util.Properties
import javax.inject.Inject


@CacheableTask
@Suppress("unused")
abstract class ClasspathManifest @Inject constructor(
    providers: ProviderFactory
) : DefaultTask() {

    @get:Internal
    abstract val archiveBaseName: Property<String>

    @get:Internal
    abstract val archiveBaseNamesByProjectPath: MapProperty<String, String>

    @get:Internal
    abstract val generatedResourcesDir: DirectoryProperty

    @get:Internal
    abstract val additionalProjects: ListProperty<String>

    @get:Internal
    abstract val optionalProjects: ListProperty<String>

    @get:Internal
    abstract val runtimeProjectDependenciesPaths: ListProperty<String>

    @get:Internal
    abstract val runtimeNonProjectDependencies: ConfigurableFileCollection

    @get:Input
    internal
    val runtime: Provider<String> = providers.provider {
        runtimeNonProjectDependencies.joinForProperties { it.name }
    }

    @get:Input
    internal
    val projects: Provider<String> = providers.provider {

        val inputProjectsArchivesBaseNames = runtimeProjectDependenciesPaths.get()
            .map { archiveBaseNamesByProjectPath.get().getValue(it) }

        val additionalProjectsArchivesBaseNames = additionalProjects.get()
            .map { archiveBaseNamesByProjectPath.get().getValue(it) }

        (inputProjectsArchivesBaseNames + additionalProjectsArchivesBaseNames)
            .joinForProperties()
    }

    @get:Input
    internal
    val optional: Provider<String> = providers.provider {
        optionalProjects.get()
            .map { archiveBaseNamesByProjectPath.get().getValue(it) }
            .joinForProperties()
    }

    @get:OutputFile
    internal
    val manifestFile: Provider<RegularFile> =
        generatedResourcesDir.file(providers.provider { "${archiveBaseName.get()}-classpath.properties" })

    @TaskAction
    fun generate() {
        ReproduciblePropertiesWriter.store(createProperties(), manifestFile.get().asFile)
    }

    private
    fun createProperties() = Properties().also { properties ->
        properties["runtime"] = runtime.get()
        properties["projects"] = projects.get()
        optional.get().takeIf { it.isNotEmpty() }?.let { optional ->
            properties["optional"] = optional
        }
    }

    private
    fun <T : Any> Iterable<T>.joinForProperties(transform: ((T) -> CharSequence)? = null) =
        joinToString(",", transform = transform)
}
