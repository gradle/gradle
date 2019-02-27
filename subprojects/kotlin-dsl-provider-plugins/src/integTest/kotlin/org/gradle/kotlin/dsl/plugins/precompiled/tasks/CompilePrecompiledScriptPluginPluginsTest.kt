/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.kotlin.dsl.plugins.precompiled.tasks

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.kotlin.dsl.fixtures.classLoaderFor

import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependencySpec

import org.junit.Ignore

import org.junit.Test

import java.net.URLClassLoader


@Ignore("wip")
class CompilePrecompiledScriptPluginPluginsTest : AbstractKotlinIntegrationTest() {

    @Test
    fun `can compile multiple source dirs`() {

        withFolders {
            "build/plugins/src" {
                "a" {
                    withFile("a.gradle.kts", """plugins { java }""")
                }
                "b" {
                    withFile("b.gradle.kts", """plugins { id("a") }""")
                }
            }
        }

        withBuildScript("""

            fun Project.pluginsDir(path: String) = layout.buildDirectory.dir("plugins/" + path)

            tasks {
                register<${CompilePrecompiledScriptPluginPlugins::class.qualifiedName}>("compilePlugins") {
                    sourceDir(pluginsDir("src/a"))
                    sourceDir(pluginsDir("src/b"))
                    outputDir.set(pluginsDir("output"))
                    classPathFiles = configurations.compileClasspath.get()
                }
            }
        """)

        build("compilePlugins")

        classLoaderFor(existing("build/plugins/output")).use { classLoader ->
            classLoader.expectPluginFromPluginsBlock(
                "A_gradle",
                "org.gradle.java"
            )
            classLoader.expectPluginFromPluginsBlock(
                "B_gradle",
                "a"
            )
        }
    }

    private
    fun URLClassLoader.expectPluginFromPluginsBlock(pluginsBlockClass: String, expectedPluginId: String) {

        val plugin = mock<PluginDependencySpec>()
        val plugins = mock<PluginDependenciesSpec> {
            on { id(any()) } doReturn plugin
        }

        evalPluginsBlockOf(pluginsBlockClass, plugins)

        inOrder(plugins, plugin) {
            verify(plugins).id(expectedPluginId)
            verifyNoMoreInteractions()
        }
    }

    private
    fun URLClassLoader.evalPluginsBlockOf(pluginsBlockClass: String, plugins: PluginDependenciesSpec) {
        loadClass(pluginsBlockClass).getDeclaredConstructor(PluginDependenciesSpec::class.java).newInstance(
            plugins
        )
    }
}
