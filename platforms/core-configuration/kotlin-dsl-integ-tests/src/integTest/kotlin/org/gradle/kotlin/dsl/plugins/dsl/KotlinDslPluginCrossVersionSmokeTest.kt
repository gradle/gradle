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

package org.gradle.kotlin.dsl.plugins.dsl

import org.gradle.integtests.fixtures.versions.KotlinGradlePluginVersions
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.kotlin.dsl.support.expectedKotlinDslPluginsVersion
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


/**
 * Assert that the cross-version protocol between `:kotlin-dsl-plugins` and `:kotlin-dsl-provider-plugins` is not broken.
 */
class KotlinDslPluginCrossVersionSmokeTest : AbstractKotlinIntegrationTest() {

    override val forceLocallyBuiltKotlinDslPlugins = false

    private val oldestSupportedKotlinDslPluginVersion = "4.1.3"

    @Test
    @Requires(IntegTestPreconditions.NotEmbeddedExecutor::class)
    fun `can run with oldest supported version of kotlin-dsl plugin`() {

        withDefaultSettingsIn("buildSrc")
        withBuildScriptIn("buildSrc", scriptWithKotlinDslPlugin(oldestSupportedKotlinDslPluginVersion)).appendText(
            """
            tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
                compilerOptions.freeCompilerArgs.add("-Xskip-metadata-version-check")
            }
            """
        )
        withFile("buildSrc/src/main/kotlin/some.gradle.kts", """println("some!")""")

        withDefaultSettings()
        withBuildScript("""plugins { id("some") }""")

        expectConventionDeprecations()

        build("help").apply {

            assertThat(
                output,
                containsString("This version of Gradle expects version '$expectedKotlinDslPluginsVersion' of the `kotlin-dsl` plugin but version '$oldestSupportedKotlinDslPluginVersion' has been applied to project ':buildSrc'. Let Gradle control the version of `kotlin-dsl` by removing any explicit `kotlin-dsl` version constraints from your build logic.")
            )

            assertThat(
                output,
                containsString("some!")
            )
        }
    }

    @Test
    @Requires(
        IntegTestPreconditions.NotEmbeddedExecutor::class,
        reason = "Kotlin version leaks on the classpath when running embedded"
    )
    fun `can build plugin for oldest supported Kotlin language version using last published plugin`() {

        `can build plugin for oldest supported Kotlin language version`()
    }

    @Test
    @Requires(
        IntegTestPreconditions.NotEmbeddedExecutor::class,
        reason = "Kotlin version leaks on the classpath when running embedded"
    )
    fun `can build plugin for oldest supported Kotlin language version using locally built plugin`() {

        doForceLocallyBuiltKotlinDslPlugins()

        `can build plugin for oldest supported Kotlin language version`()
    }

    private
    fun `can build plugin for oldest supported Kotlin language version`() {

        val oldestKotlinLanguageVersion = KotlinGradlePluginVersions.getLANGUAGE_VERSIONS().first()

        withDefaultSettingsIn("producer")
        withBuildScriptIn("producer", scriptWithKotlinDslPlugin()).appendText(
            """
            tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
                compilerOptions {
                    languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.fromVersion("$oldestKotlinLanguageVersion")
                    apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.fromVersion("$oldestKotlinLanguageVersion")
                }
            }
            """
        )
        withFile("producer/src/main/kotlin/some.gradle.kts", """println("some!")""")

        withDefaultSettings().appendText("""includeBuild("producer")""")
        withBuildScript("""plugins { id("some") }""")

        repeat(2) {
            executer.expectDeprecationWarning("w: Language version $oldestKotlinLanguageVersion is deprecated and its support will be removed in a future version of Kotlin")
        }

        build("help").apply {
            assertThat(output, containsString("some!"))
        }
    }

    @Test
    @Requires(
        IntegTestPreconditions.NotEmbeddedExecutor::class,
        reason = "Kotlin version leaks on the classpath when running embedded"
    )
    fun `can build plugin for previous unsupported Kotlin language version`() {

        val previousKotlinLanguageVersion = "1.4"

        withDefaultSettingsIn("producer")
        withBuildScriptIn(
            "producer",
            """
            plugins {
                `kotlin-dsl` version "$oldestSupportedKotlinDslPluginVersion"
            }

            $repositoriesBlock

            tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
                compilerOptions {
                    languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.fromVersion("$previousKotlinLanguageVersion")
                    apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.fromVersion("$previousKotlinLanguageVersion")
                    freeCompilerArgs.add("-Xskip-metadata-version-check")
                }
            }
            """
        )
        withFile("producer/src/main/kotlin/some.gradle.kts", """println("some!")""")

        withDefaultSettings().appendText("""includeBuild("producer")""")
        withBuildScript("""plugins { id("some") }""")

        expectConventionDeprecations()
        executer.expectDeprecationWarning("w: Language version 1.4 is deprecated and its support will be removed in a future version of Kotlin")

        build("help").apply {
            assertThat(output, containsString("some!"))
        }
    }

    private
    fun expectConventionDeprecations() {
        executer.expectDocumentedDeprecationWarning(
            "The org.gradle.api.plugins.Convention type has been deprecated. " +
                "This is scheduled to be removed in Gradle 9.0. " +
                "Consult the upgrading guide for further information: " +
                "https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_access_to_conventions"
        )
    }
}
