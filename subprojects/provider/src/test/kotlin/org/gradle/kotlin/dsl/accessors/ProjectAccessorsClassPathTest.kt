/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.kotlin.dsl.accessors

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.plugins.ApplicationPluginConvention
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer

import org.gradle.internal.classpath.DefaultClassPath

import org.gradle.kotlin.dsl.fixtures.AbstractDslTest
import org.gradle.kotlin.dsl.fixtures.testCompilationClassPath

import org.junit.Test


class ProjectAccessorsClassPathTest : AbstractDslTest() {

    @Test
    fun `#buildAccessorsFor`() {

        // given:
        val schema =
            ProjectSchema(
                extensions = listOf(
                    ProjectSchemaEntry(
                        Project::class.qualifiedName!!,
                        "sourceSets",
                        SourceSetContainer::class.qualifiedName!!
                    )
                ),
                containerElements = listOf(
                    ProjectSchemaEntry(
                        SourceSetContainer::class.qualifiedName!!,
                        "main",
                        SourceSet::class.qualifiedName!!
                    )
                ),
                conventions = listOf(
                    ProjectSchemaEntry(
                        Project::class.qualifiedName!!,
                        "application",
                        ApplicationPluginConvention::class.qualifiedName!!
                    )
                ),
                tasks = listOf(
                    ProjectSchemaEntry(
                        TaskContainer::class.qualifiedName!!,
                        "clean",
                        Delete::class.qualifiedName!!
                    )
                ),
                configurations = listOf("api")
            )
        val srcDir = newFolder("src")
        val binDir = newFolder("bin")

        // when:
        buildAccessorsFor(
            projectSchema = schema,
            classPath = testCompilationClassPath,
            srcDir = srcDir,
            binDir = binDir
        )

        // then:
        val apiConfiguration = mock<NamedDomainObjectProvider<Configuration>>()
        val configurations = mock<ConfigurationContainer> {
            on { named(any<String>()) } doReturn apiConfiguration
        }
        val sourceSets = mock<SourceSetContainer>()
        val extensions = mock<ExtensionContainer> {
            on { getByName(any()) } doReturn sourceSets
        }
        val dependencies = mock<DependencyHandler>()
        val tasks = mock<TaskContainer>()
        val project = mock<Project> {
            on { getConfigurations() } doReturn configurations
            on { getExtensions() } doReturn extensions
            on { getDependencies() } doReturn dependencies
            on { getTasks() } doReturn tasks
        }
        project.eval(
            script = """
                val a: NamedDomainObjectProvider<Configuration> = configurations.api

                val b: Dependency? = dependencies.api("module")

                val c: SourceSetContainer = sourceSets

                val d: Unit = sourceSets {}

                val e: NamedDomainObjectProvider<SourceSet> = sourceSets.main

                val f: TaskProvider<Delete> = tasks.clean

                fun Project.canUseAccessorsFromConfigurationsScope() {
                    configurations {
                        api {
                            outgoing.variants
                        }
                    }
                }
            """,
            scriptCompilationClassPath = DefaultClassPath.of(binDir) + testCompilationClassPath
// Runtime is not required because the accessores are inlined
//            scriptRuntimeClassPath = DefaultClassPath.of(binDir)
        )

        inOrder(project, configurations, apiConfiguration, extensions, sourceSets, dependencies, tasks) {
            // val a
            verify(project).configurations
            verify(configurations).named("api")

            // val b
            verify(project).dependencies
            verify(dependencies).add("api", "module")

            // val c
            verify(project).extensions
            verify(extensions).getByName("sourceSets")

            // val d
            verify(project).extensions
            verify(extensions).configure(eq("sourceSets"), any<Action<*>>())

            // val e
            verify(project).extensions
            verify(extensions).getByName("sourceSets")
            verify(sourceSets).named("main", SourceSet::class.java)

            // val f
            verify(project).tasks
            verify(tasks).named("clean", Delete::class.java)

            verifyNoMoreInteractions()
        }
    }
}
