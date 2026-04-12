/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.fixtures.executer.NoDaemonGradleExecuter
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.kotlin.dsl.embeddedKotlinVersion
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.test.fixtures.dsl.GradleDsl
import org.junit.Test

/**
 * Verifies that plugins compiled against an earlier Gradle version that picked
 * up the catch-all `T.provideDelegate` extension (e.g. via `by lazy { }` after
 * `import org.gradle.kotlin.dsl.provideDelegate`) keep loading on the current
 * Gradle version where that extension is hidden at source level but kept for
 * binary compatibility.
 *
 * See NamedDomainObjectCollectionExtensions
 * See [KT-25810](https://youtrack.jetbrains.com/issue/KT-25810).
 */
class ProvideDelegateBinaryCompatibilityIntegrationTest : AbstractKotlinIntegrationTest() {

    /**
     * Pinned to a version that still exposed the catch-all `provideDelegate`
     * extension as part of the public Kotlin DSL API.
     */
    private
    val previousGradleVersion = "9.4.1"

    @Test
    fun `plugin compiled against previous Gradle using 'by lazy' loads on current Gradle`() {

        val previousDistribution = ReleasedVersionDistributions(buildContext).getDistribution(previousGradleVersion)
            ?: error("Gradle $previousGradleVersion not in released-versions.json")

        val pluginRepo = newDir("plugin-repo")

        withFile("plugin/settings.gradle.kts", """rootProject.name = "my-plugin"""")
        withFile(
            "plugin/build.gradle.kts",
            """
            plugins {
                kotlin("jvm") version "$embeddedKotlinVersion"
                `java-gradle-plugin`
                `maven-publish`
            }
            group = "com.example"
            version = "1.0"
            java { targetCompatibility = JavaVersion.VERSION_17 }
            kotlin { compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 } }
            ${RepoScriptBlockUtil.mavenCentralRepository(GradleDsl.KOTLIN)}
            dependencies { compileOnly(gradleKotlinDsl()) }
            gradlePlugin.plugins.create("myPlugin") {
                id = "com.example.my-plugin"
                implementationClass = "com.example.MyPlugin"
            }
            publishing { repositories { maven { url = uri("${pluginRepo.toURI()}") } } }
            """.trimIndent()
        )
        withFile(
            "plugin/src/main/kotlin/com/example/MyPlugin.kt",
            """
            package com.example

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            // Bringing the catch-all extension into scope makes `by lazy { }`
            // resolve through it under Gradle 9.x, baking an INVOKESTATIC
            // reference to NamedDomainObjectCollectionExtensionsKt.provideDelegate
            // into the plugin bytecode.
            import org.gradle.kotlin.dsl.provideDelegate

            class MyPlugin : Plugin<Project> {
                override fun apply(project: Project) {
                    val computed: String by lazy { "computed-value" }
                    project.tasks.register("printComputed") { task ->
                        task.doLast { println("computed=" + computed) }
                    }
                }
            }
            """.trimIndent()
        )
        val pluginDir = existing("plugin")

        NoDaemonGradleExecuter(previousDistribution, testDirectoryProvider, buildContext)
            .inDirectory(pluginDir)
            .withTasks("publish")
            .withRepositoryMirrors()
            .noDeprecationChecks()
            .run()

        withFile(
            "consumer/settings.gradle.kts",
            """
            pluginManagement { repositories { maven { url = uri("${pluginRepo.toURI()}") } } }
            rootProject.name = "consumer"
            """.trimIndent()
        )
        withFile(
            "consumer/build.gradle.kts",
            """plugins { id("com.example.my-plugin") version "1.0" }"""
        )
        val consumerDir = existing("consumer")

        val result = inDirectory(consumerDir).withTasks("printComputed").run()
        result.assertOutputContains("computed=computed-value")
    }
}
