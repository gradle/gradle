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

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.initialization.buildsrc.BuildSrcProjectConfigurationAction
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.provider.inClassPathMode
import org.gradle.kotlin.dsl.provider.inLenientMode
import org.gradle.kotlin.dsl.resolver.buildSrcSourceRootsFilePath
import org.gradle.language.base.plugins.LifecycleBasePlugin


internal
class BuildSrcClassPathModeConfigurationAction : BuildSrcProjectConfigurationAction {

    override fun execute(project: ProjectInternal) = project.run {
        if (inClassPathMode()) {
            afterEvaluate {
                if (inLenientMode()) {
                    disableVerificationTasks()
                }
                configureBuildSrcSourceRootsTask()
            }
        }
    }

    private
    fun Project.disableVerificationTasks() {
        allprojects { project ->
            project.tasks.matching { it.group == LifecycleBasePlugin.VERIFICATION_GROUP }.configureEach { task ->
                task.enabled = false
            }
        }
    }

    private
    fun Project.configureBuildSrcSourceRootsTask() {
        plugins.withType<JavaBasePlugin> {
            tasks {
                val generateSourceRoots by registering(GenerateSourceRootsFile::class) {
                    sourceRoots.set(projectDependenciesSourceRootsFrom("runtimeClasspath"))
                    destinationFile.set(layout.projectDirectory.file(buildSrcSourceRootsFilePath))
                }
                named("jar") {
                    it.finalizedBy(generateSourceRoots)
                }
            }
        }
    }

    private
    fun Project.projectDependenciesSourceRootsFrom(configurationName: String) = provider {
        configurations.getByName(configurationName).incoming.resolutionResult.allComponents.asSequence()
            .projectDependenciesIdentifiers()
            .map { project(it.projectPath) }
            .withJavaBasePlugin()
            .allSourceSetsRoots()
            .map { it.relativeTo(rootDir).path }
            .toList()
    }

    private
    fun Sequence<ResolvedComponentResult>.projectDependenciesIdentifiers() =
        mapNotNull { it.id as? ProjectComponentIdentifier }

    private
    fun Sequence<Project>.withJavaBasePlugin() =
        filter { it.plugins.hasPlugin(JavaBasePlugin::class.java) }

    private
    fun Sequence<Project>.allSourceSetsRoots() =
        flatMap { it.sourceSets.flatMap { it.allSource.srcDirs }.asSequence() }

    private
    val Project.sourceSets
        get() = the<SourceSetContainer>()
}


@CacheableTask
open class GenerateSourceRootsFile : DefaultTask() {

    @get:Input
    val sourceRoots = project.objects.listProperty<String>()

    @get:OutputFile
    val destinationFile = project.objects.fileProperty()

    @TaskAction
    @Suppress("unused")
    fun generateSourcePathFile() =
        destinationFile.get().asFile.printWriter().use { writer ->
            sourceRoots.get().forEach { sourceRoot ->
                writer.print(sourceRoot)
                writer.print("\n")
            }
        }
}
