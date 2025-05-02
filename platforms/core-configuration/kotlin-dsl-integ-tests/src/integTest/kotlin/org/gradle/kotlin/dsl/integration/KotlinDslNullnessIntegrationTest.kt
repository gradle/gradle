/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.junit.Test


/**
 * Integration tests for API usage specific to Kotlin DSL nullness detection.
 */
class KotlinDslNullnessIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    fun `Provider#map works with a null return value in script`() {
        withBuildScript(
            """
            provider { "thing" }.map { null }
        """
        )
        build("help")
    }

    @Test
    fun `Provider#map works with a null return value in a precompiled script`() {
        withKotlinDslPlugin()

        withFile(
            "src/main/kotlin/code.gradle.kts",
            """
            provider { "thing" }.map { null }
            """
        )

        val result = build("classes")

        result.assertTaskExecuted(":compileKotlin")
    }

    @Test
    fun `Provider#map works with a null return value in a kotlin-dsl project`() {
        withKotlinDslPlugin()

        withFile(
            "src/main/kotlin/code.kt",
            """

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class MyPlugin : Plugin<Project> {
                override fun apply(project: Project) {
                    project.run {
                        provider { "thing" }.map { null }
                    }
                }
            }

            """
        )

        val result = build("classes")

        result.assertTaskExecuted(":compileKotlin")
    }

    @Test
    fun `Provider#flatMap works with a null return value in script`() {
        withBuildScript(
            """
            val providerA: Provider<String> = provider { "thing" }.flatMap { provider { null } }
            val providerB: Provider<String> = provider { "thing" }.flatMap { null }
        """
        )
        build("help")
    }

    @Test
    fun `Provider#flatMap works with a null return value in a precompiled script`() {
        withKotlinDslPlugin()

        withFile(
            "src/main/kotlin/code.gradle.kts",
            """
            val providerA: Provider<String> = provider { "thing" }.flatMap { provider { null } }
            val providerB: Provider<String> = provider { "thing" }.flatMap { null }
            """
        )

        val result = build("classes")

        result.assertTaskExecuted(":compileKotlin")
    }

    @Test
    fun `Provider#flatMap works with a null return value in a kotlin-dsl project`() {
        withKotlinDslPlugin()

        withFile(
            "src/main/kotlin/code.kt",
            """

            import org.gradle.api.Plugin
            import org.gradle.api.Project
            import org.gradle.api.Transformer
            import org.gradle.api.provider.Provider

            class MyPlugin : Plugin<Project> {
                override fun apply(project: Project) {
                    project.run {
                        val providerA: Provider<String> = provider { "thing" }.flatMap { provider { null } }
                        val providerB: Provider<String> = provider { "thing" }.flatMap { null }
                    }
                }
            }

            """
        )

        val result = build("classes")

        result.assertTaskExecuted(":compileKotlin")
    }

    @Test
    fun `CopySpec#filter works with a null return value in script`() {
        withBuildScript(
            """
            fun eliminateEverything(spec: CopySpec) {
                spec.filter { null }
            }
        """
        )
        build("help")
    }

    @Test
    fun `CopySpec#filter works with a null return value in a precompiled script`() {
        withKotlinDslPlugin()

        withFile(
            "src/main/kotlin/code.gradle.kts",
            """
            fun eliminateEverything(spec: CopySpec) {
                spec.filter { null }
            }
            """
        )

        val result = build("classes")

        result.assertTaskExecuted(":compileKotlin")
    }

    @Test
    fun `CopySpec#filter works with a null return value in a kotlin-dsl project`() {
        withKotlinDslPlugin()

        withFile(
            "src/main/kotlin/code.kt",
            """

            import org.gradle.api.file.CopySpec

            fun eliminateEverything(spec: CopySpec) {
                spec.filter { null }
            }
            """
        )

        val result = build("classes")

        result.assertTaskExecuted(":compileKotlin")
    }

    @Test
    fun `Property#set accepts null in script`() {
        withBuildScript(
            """
            interface Some {
                val some: Property<String>
            }
            val instance = objects.newInstance<Some>()
            instance.some.set(null)
            """.trimIndent()
        )
        build("help")
    }

    @Test
    fun `Property#set accepts null in precompiled script`() {
        withKotlinBuildSrc()
        withFile(
            "buildSrc/src/main/kotlin/my-plugin.gradle.kts",
            """
            interface Some {
                val some: Property<String>
            }
            val instance = objects.newInstance<Some>()
            instance.some.set(null)
            """.trimIndent()
        )
        withBuildScript("""plugins { id("my-plugin") }""")
        val result = build("help")
        result.assertTaskExecuted(":buildSrc:compileKotlin")
    }

    @Test
    fun `Property#set accepts null in kt file in kotlin-dsl project`() {
        withKotlinBuildSrc()
        withFile(
            "buildSrc/src/main/kotlin/MyPlugin.kt",
            """
            import org.gradle.api.*
            import org.gradle.api.provider.*
            import org.gradle.kotlin.dsl.*
            interface Some {
                val some: Property<String>
            }
            class MyPlugin : Plugin<Project> {
                override fun apply(project: Project) {
                    val instance = project.objects.newInstance<Some>()
                    instance.some.set(null)
                }
            }
            """.trimIndent()
        )
        existing("buildSrc/build.gradle.kts").appendText(
            """
            gradlePlugin {
                plugins {
                    create("myPlugin") {
                        id = "my-plugin"
                        implementationClass = "MyPlugin"
                    }
                }
            }
            """.trimIndent()
        )
        withBuildScript("""plugins { id("my-plugin") }""")
        val result = build("help")
        result.assertTaskExecuted(":buildSrc:compileKotlin")
    }
}
