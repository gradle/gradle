/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.kotlin.dsl.compile

import org.junit.Test


class PrecompiledPluginsCompileAvoidanceIntegrationTest : AbstractCompileAvoidanceIntegrationTest() {

    @Test
    fun `avoids buildscript recompilation when task is configured in precompiled script plugin`() {
        val pluginId = "my-plugin"
        withPrecompiledScriptPluginInBuildSrc(
            pluginId,
            """
                println("foo")
                tasks.register("foo")
            """
        )
        withUniqueScript(
            """
                plugins {
                    id("$pluginId")
                }
            """
        )
        configureProject().assertBuildScriptCompiled().assertOutputContains("foo")

        withPrecompiledScriptPluginInBuildSrc(
            pluginId,
            """
                tasks.register("foo") { doLast { println("bar from task") } }
            """
        )
        configureProject("foo").assertBuildScriptCompilationAvoided().assertOutputContains("bar from task")
    }

    @Test
    fun `recompiles buildscript when plugins applied from a precompiled plugin change`() {
        val pluginId = "my-plugin"
        withPrecompiledScriptPluginInBuildSrc(
            pluginId,
            """
                plugins {
                    id("java-library")
                }
                println("foo")
            """
        )
        withUniqueScript(
            """
                plugins {
                    id("$pluginId")
                }
            """
        )
        configureProject().assertBuildScriptCompiled().assertOutputContains("foo")

        withPrecompiledScriptPluginInBuildSrc(
            pluginId,
            """
                plugins {
                    id("java")
                }
                println("bar")
            """
        )
        configureProject().assertBuildScriptCompiled().assertOutputContains("bar")
    }

    @Test
    fun `recompiles buildscript when plugin extension registration name changes from a precompiled plugin`() {
        val pluginId = "my-plugin"
        val extensionClass = """
            open class TestExtension {
                var message = "some-message"
            }
        """
        withPrecompiledScriptPluginInBuildSrc(
            pluginId,
            """
                $extensionClass
                project.extensions.create<TestExtension>("foo")
            """
        )
        withUniqueScript(
            """
                plugins {
                    id("$pluginId")
                }
                foo {
                    message = "foo"
                }
            """
        )
        configureProject().assertBuildScriptCompiled()

        withPrecompiledScriptPluginInBuildSrc(
            pluginId,
            """
                $extensionClass
                project.extensions.create<TestExtension>("bar")
            """
        )
        configureProjectAndExpectCompileFailure("Unresolved reference: foo")
    }

    @Test
    fun `avoids buildscript recompilation on non ABI change in precompiled script plugin`() {
        val pluginId = "my-plugin"
        withPrecompiledScriptPluginInBuildSrc(
            pluginId,
            """
                println("foo")
            """
        )
        withUniqueScript(
            """
                plugins {
                    id("$pluginId")
                }
            """
        )
        configureProject().assertBuildScriptCompiled().assertOutputContains("foo")

        withPrecompiledScriptPluginInBuildSrc(
            pluginId,
            """
                println("bar")
            """
        )
        configureProject().assertBuildScriptCompilationAvoided().assertOutputContains("bar")
    }

    @Test
    fun `recompiles buildscript when new task is registered in precompiled script plugin`() {
        val pluginId = "my-plugin"
        withPrecompiledScriptPluginInBuildSrc(
            pluginId,
            """
                println("foo")
            """
        )
        withUniqueScript(
            """
                plugins {
                    id("$pluginId")
                }
            """
        )
        configureProject().assertBuildScriptCompiled().assertOutputContains("foo")

        withPrecompiledScriptPluginInBuildSrc(
            pluginId,
            """
                println("bar")
                tasks.register("foo")
            """
        )
        configureProject().assertBuildScriptCompiled().assertOutputContains("bar")
    }

    private
    fun withPrecompiledScriptPluginInBuildSrc(pluginId: String, pluginSource: String) {
        withKotlinDslPluginInBuildSrc()
        withFile("buildSrc/src/main/kotlin/$pluginId.gradle.kts", pluginSource)
    }
}
