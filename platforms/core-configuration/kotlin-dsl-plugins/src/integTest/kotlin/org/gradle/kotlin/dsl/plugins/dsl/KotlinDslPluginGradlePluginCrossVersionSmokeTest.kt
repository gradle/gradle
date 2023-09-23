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

package org.gradle.kotlin.dsl.plugins.dsl

import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.versions.KotlinGradlePluginVersions
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinDslPluginsIntegrationTest
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.util.internal.VersionNumber
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized


@RunWith(Parameterized::class)
class KotlinDslPluginGradlePluginCrossVersionSmokeTest(

    private
    val kotlinVersion: String

) : AbstractKotlinDslPluginsIntegrationTest() {

    companion object {

        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun testedKotlinVersions() = KotlinGradlePluginVersions().latestsStableOrRC
    }

    @Test
    @LeaksFileHandles("Kotlin Compiler Daemon working directory")
    fun `kotlin-dsl plugin in buildSrc and production code using kotlin-gradle-plugin `() {

        assumeNonEmbeddedGradleExecuter() // newer Kotlin version always leaks on the classpath when running embedded

        KotlinGradlePluginVersions.assumeCurrentJavaVersionIsSupportedBy(kotlinVersion)

        withDefaultSettingsIn("buildSrc")
        withBuildScriptIn(
            "buildSrc",
            """
            import org.jetbrains.kotlin.config.KotlinCompilerVersion

            plugins {
                `kotlin-dsl`
            }

            ${RepoScriptBlockUtil.mavenCentralRepository(GradleDsl.KOTLIN)}

            dependencies {
                implementation(kotlin("gradle-plugin", "$kotlinVersion"))
            }

            println("buildSrc build script classpath kotlin compiler version " + KotlinCompilerVersion.VERSION)
            """
        )
        withFile(
            "buildSrc/src/main/kotlin/my-plugin.gradle.kts",
            "apply(plugin = \"kotlin\")"
        )

        withBuildScript(
            """
            import org.jetbrains.kotlin.config.KotlinCompilerVersion

            plugins {
                `my-plugin`
            }

            ${RepoScriptBlockUtil.mavenCentralRepository(GradleDsl.KOTLIN)}

            dependencies {
                implementation(kotlin("stdlib"))
            }

            println("root build script classpath kotlin compiler version " + KotlinCompilerVersion.VERSION)
            """
        )
        withFile("src/main/kotlin/SomeSource.kt", "fun main(args: Array<String>) {}")


        if (kotlinVersion.startsWith("1.6")) {
            executer.expectDocumentedDeprecationWarning("The AbstractCompile.destinationDir property has been deprecated. This is scheduled to be removed in Gradle 9.0. Please use the destinationDirectory property instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#compile_task_wiring")
        }

        if (VersionNumber.parse(kotlinVersion) < VersionNumber.parse("1.7.0") && GradleContextualExecuter.isConfigCache()) {
            executer.expectDocumentedDeprecationWarning(
                "The org.gradle.api.plugins.BasePluginConvention type has been deprecated. " +
                    "This is scheduled to be removed in Gradle 9.0. " +
                    "Consult the upgrading guide for further information: " +
                    "https://docs.gradle.org/current/userguide/upgrading_version_8.html#base_convention_deprecation")
        }
        if (VersionNumber.parse(kotlinVersion) < VersionNumber.parse("1.7.20")) {
            executer.expectDocumentedDeprecationWarning(
                "The org.gradle.util.WrapUtil type has been deprecated. " +
                    "This is scheduled to be removed in Gradle 9.0. " +
                    "Consult the upgrading guide for further information: " +
                    "https://docs.gradle.org/current/userguide/upgrading_version_7.html#org_gradle_util_reports_deprecations"
            )
            executer.expectDocumentedDeprecationWarning(
                "The org.gradle.api.plugins.JavaPluginConvention type has been deprecated. " +
                    "This is scheduled to be removed in Gradle 9.0. " +
                    "Consult the upgrading guide for further information: " +
                    "https://docs.gradle.org/current/userguide/upgrading_version_8.html#java_convention_deprecation")
            executer.expectDocumentedDeprecationWarning(
                "The Project.getConvention() method has been deprecated. " +
                    "This is scheduled to be removed in Gradle 9.0. " +
                    "Consult the upgrading guide for further information: " +
                    "https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_access_to_conventions"
            )
            executer.expectDocumentedDeprecationWarning(
                "The org.gradle.api.plugins.Convention type has been deprecated. " +
                    "This is scheduled to be removed in Gradle 9.0. " +
                    "Consult the upgrading guide for further information: " +
                    "https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_access_to_conventions"
            )
        }
        if (VersionNumber.parse(kotlinVersion) < VersionNumber.parse("1.8.0") && GradleContextualExecuter.isConfigCache()) {
            executer.expectDocumentedDeprecationWarning(
                "The Provider.forUseAtConfigurationTime method has been deprecated. " +
                    "This is scheduled to be removed in Gradle 9.0. " +
                    "Simply remove the call. " +
                    "Consult the upgrading guide for further information: " +
                    "https://docs.gradle.org/current/userguide/upgrading_version_7.html#for_use_at_configuration_time_deprecation")
        }

        build("classes").apply {
            assertThat(
                output,
                allOf(
                    containsString("buildSrc build script classpath kotlin compiler version $embeddedKotlinVersion"),
                    containsString("root build script classpath kotlin compiler version $kotlinVersion")
                )
            )
        }
    }
}
