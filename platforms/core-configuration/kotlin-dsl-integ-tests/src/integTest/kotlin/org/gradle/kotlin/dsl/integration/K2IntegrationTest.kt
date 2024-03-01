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

package org.gradle.kotlin.dsl.integration

import org.gradle.integtests.fixtures.RepoScriptBlockUtil.mavenCentralRepository
import org.gradle.integtests.fixtures.versions.KotlinGradlePluginVersions
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.test.fixtures.dsl.GradleDsl.KOTLIN
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.junit.Test


class K2IntegrationTest : AbstractKotlinIntegrationTest() {

    private
    val kotlinVersion = KotlinGradlePluginVersions().latestStableOrRC

    @Test
    @Requires(IntegTestPreconditions.NotEmbeddedExecutor::class)
    fun `can try k2 with included build for build logic using kotlin-jvm plugin`() {

        withDefaultSettingsIn("build-logic")
        withBuildScriptIn("build-logic", """
            plugins {
                id("java-gradle-plugin")
                kotlin("jvm") version "$kotlinVersion"
            }
            ${mavenCentralRepository(KOTLIN)}
            gradlePlugin {
                plugins {
                    register("my-plugin") {
                        id = "my-plugin"
                        implementationClass = "MyPlugin"
                    }
                }
            }
        """)
        withK2BuildLogic()

        withK2BuildLogicConsumingBuild()

        assertCanConsumeK2BuildLogic()
    }

    @Test
    @Requires(IntegTestPreconditions.NotEmbeddedExecutor::class)
    fun `can try k2 with included build for build logic using kotlin-dsl plugin`() {

        // This test doesn't use a .gradle.kts precompiled script because K2 doesn't support scripts yet

        withDefaultSettingsIn("build-logic")
        withBuildScriptIn("build-logic", """
            import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
            import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

            plugins {
                `kotlin-dsl`
                kotlin("jvm") version "$kotlinVersion"
            }
            ${mavenCentralRepository(KOTLIN)}
            tasks.withType<KotlinCompile>().configureEach {
                compilerOptions {
                    apiVersion = KotlinVersion.KOTLIN_2_0
                    languageVersion = KotlinVersion.KOTLIN_2_0
                }
            }
            gradlePlugin {
                plugins {
                    register("my-plugin") {
                        id = "my-plugin"
                        implementationClass = "MyPlugin"
                    }
                }
            }
        """)
        withK2BuildLogic()

        withK2BuildLogicConsumingBuild()

        assertCanConsumeK2BuildLogic()
    }

    private
    fun withK2BuildLogic() {
        withFile("build-logic/gradle.properties", "kotlin.experimental.tryK2=true")
        withFile("build-logic/src/main/kotlin/MyTask.kt", """
            import org.gradle.api.DefaultTask
            import org.gradle.api.tasks.TaskAction

            abstract class MyTask : DefaultTask() {
                @TaskAction fun action() { println("Doing something") }
            }
        """)
        withFile("build-logic/src/main/kotlin/MyPlugin.kt", """
            import org.gradle.api.Project
            import org.gradle.api.Plugin

            class MyPlugin : Plugin<Project> {
                override fun apply(project: Project) {}
            }
        """)
    }

    private
    fun withK2BuildLogicConsumingBuild() {
        withSettings("""
            pluginManagement {
                ${mavenCentralRepository(KOTLIN)}
                includeBuild("build-logic")
            }
            rootProject.name = "k2-gradle"
        """)
        withBuildScript("""
            plugins { id("my-plugin") }
            project.tasks.register("myTask", MyTask::class)
        """)
    }

    private
    fun assertCanConsumeK2BuildLogic() {
        build("help").apply {
            assertOutputContains("ATTENTION: 'kotlin.experimental.tryK2' is an experimental option enabled in the project for trying out the new Kotlin K2 compiler only.")
            assertOutputContains("w: Language version 2.0 is experimental, there are no backwards compatibility guarantees for new language and library features")
        }
    }
}
