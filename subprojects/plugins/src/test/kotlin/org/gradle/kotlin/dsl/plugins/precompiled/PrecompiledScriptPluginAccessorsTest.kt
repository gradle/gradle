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

package org.gradle.kotlin.dsl.plugins.precompiled

import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock

import org.gradle.api.Project
import org.gradle.api.plugins.PluginManager

import org.gradle.kotlin.dsl.fixtures.bytecode.InternalName
import org.gradle.kotlin.dsl.fixtures.bytecode.publicClass
import org.gradle.kotlin.dsl.fixtures.normalisedPath
import org.gradle.kotlin.dsl.fixtures.pluginDescriptorEntryFor
import org.gradle.kotlin.dsl.support.zipTo

import org.junit.Test
import java.io.File


class PrecompiledScriptPluginAccessorsTest : AbstractPrecompiledScriptPluginTest() {

    @Test
    fun `can use core plugin spec builders`() {

        givenPrecompiledKotlinScript("java-project.gradle.kts", """

            plugins {
                java
            }

        """)

        val (project, pluginManager) = projectAndPluginManagerMocks()

        instantiatePrecompiledScriptOf(
            project,
            "Java_project_gradle"
        )

        inOrder(pluginManager) {
            verify(pluginManager).apply("org.gradle.java")
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `can use plugin spec builders for plugins in the implementation classpath`() {

        // given:
        val pluginJar = pluginJarWith(
            pluginDescriptorEntryFor("my.plugin", "MyPlugin"),
            "MyPlugin.class" to publicClass(InternalName("MyPlugin"))
        )

        withKotlinDslPlugin().appendText("""

            dependencies {
                implementation(files("${pluginJar.normalisedPath}"))
            }

        """)

        withPrecompiledKotlinScript("plugin.gradle.kts", """

            plugins {
                my.plugin
            }

        """)

        compileKotlin()

        val (project, pluginManager) = projectAndPluginManagerMocks()

        instantiatePrecompiledScriptOf(
            project,
            "Plugin_gradle"
        )

        inOrder(pluginManager) {
            verify(pluginManager).apply("my.plugin")
            verifyNoMoreInteractions()
        }
    }

    private
    fun pluginJarWith(vararg entries: Pair<String, ByteArray>): File =
        newFile("my.plugin.jar").also { file ->
            zipTo(file, entries.asSequence())
        }

    private
    fun projectAndPluginManagerMocks(): Pair<Project, PluginManager> {
        val pluginManager = mock<PluginManager>()
        val project = mock<Project> {
            on { getPluginManager() } doReturn pluginManager
            on { project } doAnswer { it.mock as Project }
        }
        return Pair(project, pluginManager)
    }
}
