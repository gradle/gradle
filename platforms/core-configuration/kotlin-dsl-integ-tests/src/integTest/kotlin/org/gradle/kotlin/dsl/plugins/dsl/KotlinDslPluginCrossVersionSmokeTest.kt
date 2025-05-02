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

import org.gradle.api.JavaVersion
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.kotlin.dsl.support.expectedKotlinDslPluginsVersion
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions.NotEmbeddedExecutor
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.io.File


/**
 * Assert that the cross-version protocol between `:kotlin-dsl-plugins` and `:kotlin-dsl-provider-plugins` is not broken.
 *
 * Note that using a different version of the `kotlin-dsl` plugins than the one blessed by the Gradle Version is not supported.
 * Users doing that are getting a warning, these test scenarios expect that warning.
 *
 * In other words, breaking these tests is not considered a breakage for a minor Gradle version.
 * These tests represent the best effort we are achieving.
 */
@Suppress("FunctionName")
class KotlinDslPluginCrossVersionSmokeTest : AbstractKotlinIntegrationTest() {

    override val forceLocallyBuiltKotlinDslPlugins = false

    private val oldestSupportedKotlinDslPluginVersion = "4.2.0"

    @Test
    @Requires(NotEmbeddedExecutor::class)
    fun `can run with oldest supported version of kotlin-dsl plugin`() {

        withDefaultSettingsIn("buildSrc")
        val buildScript = withBuildScriptIn("buildSrc", scriptWithKotlinDslPlugin(oldestSupportedKotlinDslPluginVersion))
        buildScript.appendText(
            """
            tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
                compilerOptions.freeCompilerArgs.add("-Xskip-metadata-version-check")
            }
            """
        )
        buildScript.appendJvmTargetCompatibility()
        withFile("buildSrc/src/main/kotlin/some.gradle.kts", """println("some!")""")

        withDefaultSettings()
        withBuildScript("""plugins { id("some") }""")

        expectConventionDeprecations()
        expectConfigurationCacheRequestedDeprecation()

        build("help").apply {

            assertThat(
                output,
                containsString("This version of Gradle expects version '$expectedKotlinDslPluginsVersion' of the `kotlin-dsl` plugin " +
                    "but version '$oldestSupportedKotlinDslPluginVersion' has been applied to project ':buildSrc'. " +
                    "Let Gradle control the version of `kotlin-dsl` by removing any explicit `kotlin-dsl` version constraints from your build logic.")
            )

            assertThat(
                output,
                containsString("some!")
            )
        }
    }

    @Test
    @Requires(NotEmbeddedExecutor::class, reason = "Kotlin version leaks on the classpath when running embedded")
    fun `can build plugin for previous unsupported Kotlin language version`() {

        val previousKotlinLanguageVersion = "1.4"

        withDefaultSettingsIn("producer")
        val buildScript = withBuildScriptIn(
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
        buildScript.appendJvmTargetCompatibility()
        withFile("producer/src/main/kotlin/some.gradle.kts", """println("some!")""")

        withDefaultSettings().appendText("""includeBuild("producer")""")
        withBuildScript("""plugins { id("some") }""")

        expectConventionDeprecations()
        expectConfigurationCacheRequestedDeprecation()
        executer.expectDeprecationWarning("w: Language version 1.4 is deprecated and its support will be removed in a future version of Kotlin")

        build("help").apply {
            assertThat(output, containsString("some!"))
        }
    }

    private fun File.appendJvmTargetCompatibility() {
        if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_20)) {
            // Kotlin 1.8.20 that is a dependency of the older kotlin-dsl plugin doesn't work
            // with Java20+ without setting jvmTarget that is lower than JvmTarget.JVM_20
            appendText(
                """
                tasks.named<JavaCompile>("compileJava") {
                    options.release = 8
                }
                tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
                    compilerOptions {
                        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8
                    }
                }"""
            )
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

    private
    fun expectConfigurationCacheRequestedDeprecation() {
        executer.expectDocumentedDeprecationWarning(
            "The StartParameter.isConfigurationCacheRequested property has been deprecated. " +
                "This is scheduled to be removed in Gradle 10.0. " +
                "Please use 'configurationCache.requested' property on 'BuildFeatures' service instead. Consult the upgrading guide for further information:" +
                " https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_startparameter_is_configuration_cache_requested"
        )
    }
}
