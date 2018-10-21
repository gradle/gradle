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
import com.nhaarman.mockito_kotlin.same

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.DependencyConstraintHandler
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.plugins.ApplicationPluginConvention
import org.gradle.api.plugins.Convention
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer

import org.gradle.internal.classpath.DefaultClassPath

import org.gradle.kotlin.dsl.fixtures.AbstractDslTest
import org.gradle.kotlin.dsl.fixtures.testCompilationClassPath
import org.gradle.kotlin.dsl.project

import org.gradle.nativeplatform.BuildType

import org.junit.Test

import org.mockito.ArgumentMatchers.anyMap


class ProjectAccessorsClassPathTest : AbstractDslTest() {

    inline fun <reified ReceiverType, reified ReturnType> entry(name: String): ProjectSchemaEntry<SchemaType> =
        ProjectSchemaEntry(SchemaType.of<ReceiverType>(), name, SchemaType.of<ReturnType>())

    @Test
    fun `#buildAccessorsFor`() {

        // given:
        val schema =
            TypedProjectSchema(
                extensions = listOf(
                    entry<Project, SourceSetContainer>("sourceSets"),
                    entry<Project, NamedDomainObjectContainer<BuildType>>("buildTypes")
                ),
                containerElements = listOf(
                    entry<SourceSetContainer, SourceSet>("main")
                ),
                conventions = listOf(
                    entry<Project, ApplicationPluginConvention>("application")
                ),
                tasks = listOf(
                    entry<TaskContainer, Delete>("clean")
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
            on { named(any<String>(), any<Class<Configuration>>()) } doReturn apiConfiguration
        }
        val sourceSets = mock<SourceSetContainer>()
        val extensions = mock<ExtensionContainer> {
            on { getByName(any()) } doReturn sourceSets
        }
        val constraint = mock<DependencyConstraint>()
        val constraints = mock<DependencyConstraintHandler> {
            on { add(any(), any()) } doReturn constraint
            on { add(any(), any(), any()) } doReturn constraint
        }
        val dependency = mock<ExternalModuleDependency>()
        val projectDependency = mock<ProjectDependency>()
        val dependencies = mock<DependencyHandler> {
            on { create(any()) } doReturn dependency
            on { getConstraints() } doReturn constraints
            on { project(anyMap<String, Any?>()) } doReturn projectDependency
        }
        val tasks = mock<TaskContainer>()
        val applicationPluginConvention = mock<ApplicationPluginConvention>()
        val convention = mock<Convention> {
            on { plugins } doReturn mapOf("application" to applicationPluginConvention)
        }
        val project = mock<Project> {
            on { getConfigurations() } doReturn configurations
            on { getExtensions() } doReturn extensions
            on { getDependencies() } doReturn dependencies
            on { getTasks() } doReturn tasks
            on { getConvention() } doReturn convention
        }
        project.eval(
            script = """
                val a: NamedDomainObjectProvider<Configuration> = configurations.api

                val b: Dependency? = dependencies.api("module")

                val c: SourceSetContainer = sourceSets

                val d: Unit = sourceSets {}

                val e: NamedDomainObjectProvider<SourceSet> = sourceSets.main

                val f: TaskProvider<Delete> = tasks.clean

                val g: Dependency? = dependencies.api("module") {
                    val module: ExternalModuleDependency = this
                }

                val h: Unit = buildTypes {
                    val container: NamedDomainObjectContainer<BuildType> = this
                }

                val i: ApplicationPluginConvention = application

                val j: Unit = application {
                    val convention: ApplicationPluginConvention = this
                }

                val k: DependencyConstraint? = dependencies.constraints.api("direct:accessor:1.0")
                val l: DependencyConstraint? = dependencies.constraints.api("direct:accessor-with-action") {
                    val constraint: DependencyConstraint = this
                }

                val projectDependency = dependencies.project(":core")
                val m: ProjectDependency = dependencies.api(projectDependency) {
                    val dependency: ProjectDependency = this
                }

                val n: ExternalModuleDependency = dependencies.api(group = "g", name = "n")/* {
                    val dependency: ExternalModuleDependency = this
                }*/

                fun Project.canUseAccessorsFromConfigurationsScope() {
                    configurations {
                        api {
                            outgoing.variants
                        }
                    }
                }
            """,
            scriptCompilationClassPath = DefaultClassPath.of(binDir) + testCompilationClassPath,
            scriptRuntimeClassPath = DefaultClassPath.of(binDir)
        )

        inOrder(
            project,
            configurations,
            apiConfiguration,
            extensions,
            sourceSets,
            dependencies,
            tasks,
            convention,
            applicationPluginConvention,
            constraints
        ) {
            // val a
            verify(project).configurations
            verify(configurations).named("api", Configuration::class.java)

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

            // val g
            verify(project).dependencies
            verify(dependencies).create("module")
            verify(dependencies).add("api", dependency)

            // val h
            verify(project).extensions
            verify(extensions).configure(eq("buildTypes"), any<Action<*>>())

            // val i
            verify(project).convention
            verify(convention).plugins

            // val j
            verify(project).convention
            verify(convention).plugins

            // val k
            verify(project).dependencies
            verify(dependencies).constraints
            verify(constraints).add(eq("api"), eq("direct:accessor:1.0"))

            // val l
            verify(project).dependencies
            verify(dependencies).constraints
            verify(constraints).add(eq("api"), eq("direct:accessor-with-action"), any())

            // val m
            verify(project).dependencies
            verify(dependencies).project(path = ":core")
            verify(project).dependencies
            verify(dependencies).add(eq("api"), same(projectDependency))

            // val n
            verify(project).dependencies
            verify(dependencies).create(mapOf("group" to "g", "name" to "n"))
            verify(dependencies).add("api", dependency)

            verifyNoMoreInteractions()
        }
    }
}
