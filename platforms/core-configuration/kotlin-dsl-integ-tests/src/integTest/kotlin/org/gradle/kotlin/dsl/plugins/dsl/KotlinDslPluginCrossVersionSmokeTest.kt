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
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
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

    // Previous versions depend on Kotlin that is not supported with Gradle >= 8.0
    val oldestSupportedKotlinDslPluginVersion = "3.2.4"

    @Test
    @Requires(IntegTestPreconditions.NotEmbeddedExecutor::class)
    fun `can run with first version of kotlin-dsl plugin supporting Gradle 8_0`() {

        withDefaultSettingsIn("buildSrc")
        withBuildScriptIn("buildSrc", scriptWithKotlinDslPlugin(oldestSupportedKotlinDslPluginVersion)).appendText(
            """
            tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
                kotlinOptions.freeCompilerArgs += "-Xskip-metadata-version-check"
            }
            """
        )
        withFile("buildSrc/src/main/kotlin/some.gradle.kts", """println("some!")""")

        withDefaultSettings()
        withBuildScript("""plugins { id("some") }""")

        expectConventionDeprecations()
        expectKotlinDslPluginDeprecation()
        if (GradleContextualExecuter.isConfigCache()) {
            expectForUseAtConfigurationTimeDeprecation()
        }

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

        val previousKotlinLanguageVersion = "1.2"

        withDefaultSettingsIn("producer")
        withBuildScriptIn(
            "producer",
            """
            plugins {
                `kotlin-dsl` version "$oldestSupportedKotlinDslPluginVersion"
            }

            $repositoriesBlock

            tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
                // compilerOptions {
                //     languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.fromVersion("$previousKotlinLanguageVersion")
                //     apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.fromVersion("$previousKotlinLanguageVersion")
                // }
                kotlinOptions {
                    languageVersion = "$previousKotlinLanguageVersion"
                    apiVersion = "$previousKotlinLanguageVersion"
                    freeCompilerArgs += "-Xskip-metadata-version-check"
                }
            }
            """
        )
        withFile("producer/src/main/kotlin/some.gradle.kts", """println("some!")""")

        withDefaultSettings().appendText("""includeBuild("producer")""")
        withBuildScript("""plugins { id("some") }""")

        expectConventionDeprecations()
        expectKotlinDslPluginDeprecation()
        if (GradleContextualExecuter.isConfigCache()) {
            expectForUseAtConfigurationTimeDeprecation()
        }

        build("help").apply {
            assertThat(output, containsString("some!"))
        }
    }

    @Test
    @Requires(IntegTestPreconditions.NotEmbeddedExecutor::class)
    fun `can run first version of kotlin-dsl plugin supporting lazy property assignment with deprecation warning`() {

        val testedVersion = "4.0.2"

        withDefaultSettingsIn("buildSrc")
        val buildScript = withBuildScriptIn("buildSrc", scriptWithKotlinDslPlugin(testedVersion))
        if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_20)) {
            // Kotlin 1.8.20 that is a dependency of kotlin-dsl plugin 4.0.2 doesn't work
            // with Java20+ without setting jvmTarget that is lower than JvmTarget.JVM_20
            buildScript.appendText(
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
        withFile("buildSrc/src/main/kotlin/some.gradle.kts", """println("some!")""")

        withDefaultSettings()
        withBuildScript("""plugins { id("some") }""")

        executer.expectDocumentedDeprecationWarning("Internal class org.gradle.kotlin.dsl.assignment.internal.KotlinDslAssignment has been deprecated. This is scheduled to be removed in Gradle 9.0. The class was most likely loaded from `kotlin-dsl` plugin version 4.1.0 or earlier version used in the build: avoid specifying a version for `kotlin-dsl` plugin.")
        executer.expectDocumentedDeprecationWarning(
            "The org.gradle.api.plugins.Convention type has been deprecated. " +
                "This is scheduled to be removed in Gradle 9.0. " +
                "Consult the upgrading guide for further information: " +
                "https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_access_to_conventions"
        )

        build("help").apply {
            assertThat(
                output,
                containsString("This version of Gradle expects version '$expectedKotlinDslPluginsVersion' of the `kotlin-dsl` plugin but version '$testedVersion' has been applied to project ':buildSrc'. Let Gradle control the version of `kotlin-dsl` by removing any explicit `kotlin-dsl` version constraints from your build logic.")
            )

            assertThat(
                output,
                containsString("some!")
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
    fun expectKotlinDslPluginDeprecation() {
        executer.expectDocumentedDeprecationWarning(
            "Using the `kotlin-dsl` plugin together with Kotlin Gradle Plugin < 1.8.0. " +
                "This behavior has been deprecated. " +
                "This will fail with an error in Gradle 9.0. " +
                "Please let Gradle control the version of `kotlin-dsl` by removing any explicit `kotlin-dsl` version constraints from your build logic. " +
                "Or use version $expectedKotlinDslPluginsVersion which is the expected version for this Gradle release. " +
                "If you explicitly declare which version of the Kotlin Gradle Plugin to use for your build logic, update it to >= 1.8.0. " +
                "Consult the upgrading guide for further information: " +
                "https://docs.gradle.org/current/userguide/upgrading_version_8.html#kotlin_dsl_with_kgp_lt_1_8_0"
        )
    }

    private
    fun expectForUseAtConfigurationTimeDeprecation() {
        executer.expectDocumentedDeprecationWarning(
            "The Provider.forUseAtConfigurationTime method has been deprecated. " +
                "This is scheduled to be removed in Gradle 9.0. " +
                "Simply remove the call. " +
                "Consult the upgrading guide for further information: " +
                "https://docs.gradle.org/current/userguide/upgrading_version_7.html#for_use_at_configuration_time_deprecation"
        )
    }
}
