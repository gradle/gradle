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
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import accessors.base
import gradlebuildJava
import org.gradle.kotlin.dsl.*

import java.io.File
import java.util.Properties


@CacheableTask
@Suppress("unused")
open class ClasspathManifest : DefaultTask() {

    @get:Classpath
    val input: Configuration = project.configurations["runtimeClasspath"]

    @get:Input
    var optionalProjects: List<String> = emptyList()

    @get:Internal
    var additionalProjects: List<Project> = emptyList()

    @get:OutputFile
    val manifestFile: File
        get() {
            return File(project.gradlebuildJava.generatedResourcesDir, "${project.base.archivesBaseName}-classpath.properties")
        }

    @get:Input
    val runtime: String
        get() = input
            .fileCollection { it is ExternalDependency || it is FileCollectionDependency }
            .joinForProperties { it.name }

    @get:Input
    val projects: String
        get() = (input.allDependencies.withType<ProjectDependency>().filter { it.dependencyProject.plugins.hasPlugin("java-base") }.map { it.dependencyProject.base.archivesBaseName }
            + additionalProjects.map { it.base.archivesBaseName })
            .joinForProperties()

    @TaskAction
    fun generate() {
        ReproduciblePropertiesWriter.store(createProperties(), manifestFile)
    }

    private
    fun createProperties() = Properties().also { properties ->
        properties["runtime"] = runtime
        properties["projects"] = projects
        if (optionalProjects.isNotEmpty()) {
            properties["optional"] = optionalProjects.joinForProperties()
        }
    }

    private
    fun <T : Any> Iterable<T>.joinForProperties(transform: ((T) -> CharSequence)? = null) =
        joinToString(",", transform = transform)
}
