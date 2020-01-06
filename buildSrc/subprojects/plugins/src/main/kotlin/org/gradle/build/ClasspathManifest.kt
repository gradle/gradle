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
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import accessors.base
import gradlebuildJava
import org.gradle.kotlin.dsl.*

import java.io.File
import java.util.Properties
import javax.inject.Inject


@CacheableTask
@Suppress("unused")
abstract class ClasspathManifest @Inject constructor(
    providers: ProviderFactory
) : DefaultTask() {

    @get:Internal
    abstract val additionalProjects: ListProperty<String>

    @get:Internal
    abstract val optionalProjects: ListProperty<String>

    @get:Input
    internal
    val runtime: Provider<String> = providers.provider {
        runtimeClasspath
            .fileCollection { it is ExternalDependency || it is FileCollectionDependency }
            .joinForProperties { it.name }
    }

    @get:Input
    internal
    val projects: Provider<String> = providers.provider {

        val inputProjectsArchivesBaseNames = runtimeClasspath.allDependencies
            .withType<ProjectDependency>()
            .filter { it.dependencyProject.plugins.hasPlugin("java-base") }
            .map { it.dependencyProject.base.archivesBaseName }

        val additionalProjectsArchivesBaseNames = additionalProjects.get()
            .map { project.project(it).base.archivesBaseName }

        (inputProjectsArchivesBaseNames + additionalProjectsArchivesBaseNames)
            .joinForProperties()
    }

    @get:Input
    internal
    val optional: Provider<String> = providers.provider {
        optionalProjects.get()
            .map { project.project(it).base.archivesBaseName }
            .joinForProperties()
    }

    @get:OutputFile
    internal
    val manifestFile: Provider<File> = providers.provider {
        project.gradlebuildJava.generatedResourcesDir
            .resolve("${project.base.archivesBaseName}-classpath.properties")
    }

    @TaskAction
    fun generate() {
        ReproduciblePropertiesWriter.store(createProperties(), manifestFile.get())
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
    val runtimeClasspath
        get() = project.configurations["runtimeClasspath"]

    private
    fun <T : Any> Iterable<T>.joinForProperties(transform: ((T) -> CharSequence)? = null) =
        joinToString(",", transform = transform)
}
