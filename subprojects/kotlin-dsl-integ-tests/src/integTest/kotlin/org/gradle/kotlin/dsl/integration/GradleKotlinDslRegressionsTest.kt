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

package org.gradle.kotlin.dsl.integration

import org.junit.Ignore
import org.junit.Test
import spock.lang.Issue


class GradleKotlinDslRegressionsTest : AbstractPluginIntegrationTest() {

    @Test
    @Issue("https://github.com/gradle/gradle/issues/9919")
    fun `gradleKotlinDsl dependency declaration does not throw`() {

        withBuildScript(
            """
            plugins { java }
            dependencies {
                compileOnly(gradleKotlinDsl())
            }
            """
        )

        build("help")
    }

    @Test
    @Issue("https://github.com/gradle/gradle/issues/12388")
    fun `provider map can return null in kotlin DSL`() {
        withDefaultSettingsIn("buildSrc")
        withBuildScriptIn(
            "buildSrc",
            """
            import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
            plugins {
                `kotlin-dsl`
            }

            $repositoriesBlock

            tasks.withType<KotlinCompile> {
                kotlinOptions.jvmTarget = "1.8"
            }
            """
        )
        withFile(
            "./buildSrc/src/main/kotlin/TestFile.kt",
            """
            import org.gradle.api.*
            import org.gradle.api.provider.*
            import java.io.File

            fun Project.testBug() {
                data class JavadocFacade(val destinationDir: File?)
                val javadocTask: Provider<JavadocFacade> = project.provider { JavadocFacade(null) }

                val provider: Provider<File> = javadocTask.map { it.destinationDir }

                require(provider.getOrNull() == null) {
                    "File should not be present"
                }
            }

            """
        )

        build("help")
    }

    @Test
    @Issue("https://github.com/gradle/gradle/issues/12388")
    @Ignore("No fix has been made for this yet")
    fun `provider flatMap can return null in kotlin DSL`() {
        withDefaultSettingsIn("buildSrc")
        withBuildScriptIn(
            "buildSrc",
            """
            import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
            plugins {
                `kotlin-dsl`
            }

            $repositoriesBlock

            tasks.withType<KotlinCompile> {
                kotlinOptions.jvmTarget = "1.8"
            }
            """
        )
        withFile(
            "./buildSrc/src/main/kotlin/TestFile.kt",
            """
            import org.gradle.api.*
            import org.gradle.api.provider.*
            import java.io.File

            fun Project.testBug() {
                data class JavadocFacade(val destinationDir: File?)
                val javadocTask: Provider<JavadocFacade> = project.provider { JavadocFacade(null) }

                val provider: Provider<File> = javadocTask.flatMap {
                    it.destinationDir?.let { project.provider { it } }
                }

                require(provider.getOrNull() == null) {
                    "File should not be present"
                }
            }

            """
        )

        build("help")
    }

    @Test
    fun `can configure ext extension`() {
        withBuildScript(
            """
            ext {
                set("foo", "bar")
            }
            """
        )

        build("help")
    }
}
