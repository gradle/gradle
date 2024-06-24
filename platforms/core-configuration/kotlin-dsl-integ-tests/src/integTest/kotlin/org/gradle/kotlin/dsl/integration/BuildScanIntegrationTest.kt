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

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.kotlin.dsl.fixtures.normalisedPath
import org.gradle.plugin.management.internal.autoapply.AutoAppliedDevelocityPlugin
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.io.File


class BuildScanIntegrationTest : AbstractKotlinIntegrationTest() {

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
                "This is scheduled to be removed in Gradle 9.0. Please use 'id(\"com.gradle.develocity\") version \"${AutoAppliedDevelocityPlugin.VERSION}\"' instead. " +
                "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#gradle_enterprise_extension_deprecated")
        executer.expectDeprecationWarning(
            "WARNING: The following functionality has been deprecated and will be removed in the next major release of the Develocity Gradle plugin. " +
                "Run with '-Ddevelocity.deprecation.captureOrigin=true' to see where the deprecated functionality is being used. " +
                "For assistance with migration, see https://gradle.com/help/gradle-plugin-develocity-migration.")
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
