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


class BuildCacheIntegrationTest : AbstractScriptCachingIntegrationTest() {

    @Test
    fun `can publish build scan`() {

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

        build("--scan", "--build-cache", "-Dscan.dump").apply {
            assertThat(output, containsString("Build scan written to"))
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
