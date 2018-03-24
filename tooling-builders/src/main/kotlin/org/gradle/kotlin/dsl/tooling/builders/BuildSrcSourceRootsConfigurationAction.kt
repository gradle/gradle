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
package org.gradle.kotlin.dsl.tooling.builders

import org.gradle.initialization.buildsrc.BuildSrcProjectConfigurationAction

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar

import org.gradle.api.internal.project.ProjectInternal

import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.provider.inClassPathMode
import org.gradle.kotlin.dsl.resolver.buildSrcSourceRootsResourcePath


private
const val buildSrcSourceRootsGeneratedDirectoryPath = "generated-resources/buildSrcLocalSourcePath"


internal
class BuildSrcSourceRootsConfigurationAction : BuildSrcProjectConfigurationAction {

    override fun execute(project: ProjectInternal) = project.run {
        if (inClassPathMode()) {
            afterEvaluate {
                configureBuildSrcSourceRootsTask()
            }
        }
    }

    private
    fun Project.configureBuildSrcSourceRootsTask() {
        plugins.withType<JavaBasePlugin> {
            tasks {
                val generatedResourcesDir = layout.buildDirectory.dir(buildSrcSourceRootsGeneratedDirectoryPath)
                val generateSourceRoots by creating(GenerateSourceRootsFile::class) {
                    sourceRoots.set(provider {
                        projectDependenciesSourceRootsFrom("runtimeClasspath")
                    })
                    destinationFile.set(provider {
                        generatedResourcesDir.get().file(buildSrcSourceRootsResourcePath)
                    })
                }
                "jar"(Jar::class) {
                    dependsOn(generateSourceRoots)
                    from(generatedResourcesDir)
                }
            }
        }
    }

    private
    fun Project.projectDependenciesSourceRootsFrom(configurationName: String) =
        configurations.getByName(configurationName).incoming.resolutionResult.allComponents.asSequence()
            .projectDependenciesIdentifiers()
            .map { project(it.projectPath) }
            .withJavaBasePlugin()
            .allSourceSetsRoots()
            .map { it.relativeTo(rootDir).path }
            .toList()

    private
    fun Sequence<ResolvedComponentResult>.projectDependenciesIdentifiers() =
        mapNotNull { it.id as? ProjectComponentIdentifier }

    private
    fun Sequence<Project>.withJavaBasePlugin() =
        filter { it.plugins.hasPlugin((JavaBasePlugin::class.java)) }

    private
    fun Sequence<Project>.allSourceSetsRoots() =
        flatMap { it.java.sourceSets.flatMap { it.allSource.srcDirs }.asSequence() }

    private
    val Project.java
        get() = the<JavaPluginConvention>()
}


@CacheableTask
open class GenerateSourceRootsFile : DefaultTask() {

    @get:Input
    @get:PathSensitive(PathSensitivity.NONE)
    val sourceRoots = project.objects.listProperty<String>()

    @get:OutputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @Suppress("LeakingThis")
    val destinationFile = newOutputFile()

    @TaskAction
    @Suppress("unused")
    fun generateSourcePathFile() =
        destinationFile.get().asFile.printWriter().use { sourceRoots.get().forEach(it::println) }
}
