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
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock

import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.plugins.ApplicationPluginConvention
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.tasks.SourceSetContainer

import org.gradle.internal.classpath.DefaultClassPath

import org.gradle.kotlin.dsl.fixtures.AbstractDslTest
import org.gradle.kotlin.dsl.fixtures.testCompilationClassPath

import org.junit.Test


class ProjectAccessorsClassPathTest : AbstractDslTest() {

    @Test
    fun `#buildAccessorsFor`() {

        // given:
        val srcDir = newFolder("src")
        val binDir = newFolder("bin")
        val schema =
            ProjectSchema(
                extensions = listOf(
                    ProjectSchemaEntry(
                        Project::class.qualifiedName!!,
                        "ext",
                        ExtraPropertiesExtension::class.qualifiedName!!
                    )
                ),
                containerElements = listOf(
                    ProjectSchemaEntry(
                        SourceSetContainer::class.qualifiedName!!,
                        "api",
                        Configuration::class.qualifiedName!!
                    )
                ),
                conventions = listOf(
                    ProjectSchemaEntry(
                        Project::class.qualifiedName!!,
                        "application",
                        ApplicationPluginConvention::class.qualifiedName!!
                    )
                ),
                tasks = emptyList(),
                configurations = listOf("api")
            )

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
        val extExtension = mock<ExtraPropertiesExtension>()
        val extensions = mock<ExtensionContainer> {
            on { getByName(any<String>()) } doReturn extExtension
        }
        val project = mock<Project> {
            on { getConfigurations() } doReturn configurations
            on { getExtensions() } doReturn extensions
        }
        project.eval(
            script = """
                val api = configurations.api
                val x = ext
            """,
            scriptCompilationClassPath = DefaultClassPath.of(binDir) + testCompilationClassPath,
            scriptRuntimeClassPath = DefaultClassPath.of(binDir)
        )

        inOrder(project, configurations, apiConfiguration, extensions, extExtension) {
            verify(project).configurations
            verify(configurations).named("api")
            verify(project).extensions
            verify(extensions).getByName("ext")
            verifyNoMoreInteractions()
        }
    }
}
