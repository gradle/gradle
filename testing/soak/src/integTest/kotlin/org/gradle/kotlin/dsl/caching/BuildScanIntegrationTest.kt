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

package org.gradle.kotlin.dsl.caching

import org.gradle.kotlin.dsl.fixtures.normalisedPath

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

import java.io.File


class BuildScanIntegrationTest : AbstractScriptCachingIntegrationTest() {

    @Test
    fun `can publish build scan using develocity extension`() {

        val buildCacheDir = existing("build-cache")

        withLocalBuildCacheSettings(buildCacheDir)

        val settingsFile = existing("settings.gradle.kts")
        settingsFile.writeText(
            """
            plugins {
                develocity
            }

            develocity.buildScan {
                termsOfUseUrl = "https://gradle.com/help/legal-terms-of-use"
                termsOfUseAgree = "yes"
            }
            """ + settingsFile.readText()
        )
        build("--scan", "--build-cache", "-Dscan.dump").apply {
            assertThat(output, containsString("Build scan written to"))
        }
    }

    @Test
    fun `using the gradle enterprise extension is deprecated`() {

        val buildCacheDir = existing("build-cache")

        withLocalBuildCacheSettings(buildCacheDir)

        val settingsFile = existing("settings.gradle.kts")
        settingsFile.writeText(
            """
            plugins {
                `gradle-enterprise`
            }

            gradleEnterprise.buildScan {
                termsOfServiceUrl = "https://gradle.com/terms-of-service"
                termsOfServiceAgree = "yes"
            }
            """ + settingsFile.readText()
        )

        executer.expectDocumentedDeprecationWarning(
            "The PluginDependencySpec.`gradle-enterprise` property has been deprecated. " +
                "This is scheduled to be removed in Gradle 9.0. Please use the develocity property instead. " +
                "For more information, please refer to https://docs.gradle.org/current/kotlin-dsl/gradle/org.gradle.kotlin.dsl/gradle-enterprise.html in the Gradle documentation.")
        executer.expectDeprecationWarning("WARNING: The following functionality has been deprecated and will be removed in the next major release of the Develocity Gradle plugin. For assistance with migration, see https://gradle.com/help/gradle-plugin-develocity-migration.")
        executer.expectDeprecationWarning("""- The deprecated "gradleEnterprise.buildScan.termsOfServiceUrl" API has been replaced by "develocity.buildScan.termsOfUseUrl"""")
        executer.expectDeprecationWarning("""- The deprecated "gradleEnterprise.buildScan.termsOfServiceAgree" API has been replaced by "develocity.buildScan.termsOfUseAgree"""")
        build("--scan", "--build-cache", "-Dscan.dump").apply {
            assertThat(output, containsString("Build scan written to"))
            assertThat(output, containsString("'`gradle-enterprise`: PluginDependencySpec' is deprecated. Gradle Enterprise has been renamed to Develocity"))
        }
    }

    private
    fun withLocalBuildCacheSettings(buildCacheDir: File): File =
        withSettings(
            """
            buildCache {
                local {
                    directory = file("${buildCacheDir.normalisedPath}")
                    isEnabled = true
                    isPush = true
                }
            }
            """
        )
}
