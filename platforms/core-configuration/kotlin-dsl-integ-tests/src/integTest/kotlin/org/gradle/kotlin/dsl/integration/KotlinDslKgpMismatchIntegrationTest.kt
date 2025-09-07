/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.integtests.fixtures.versions.KotlinGradlePluginVersions
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.util.internal.VersionNumber
import org.junit.Test

class KotlinDslKgpMismatchIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    fun `kgp and kotlin-dsl plugins applied together with clashing versions`() {
        val higherKotlinVersion = KotlinGradlePluginVersions().latests.last {
            VersionNumber.parse(it) > VersionNumber.parse(embeddedKotlinVersion)
        } // has to be a higher version than what's embedded by gradle, otherwise the `kotlin-dsl` plugin will win out

        withBuildScript(
            """
                plugins {
                    kotlin("jvm") version "$higherKotlinVersion"
                    `kotlin-dsl`
                }
            """
        )

        val result = build("build")
        result.assertOutputContains(
            expectedWarningMessage(higherKotlinVersion)
        )
    }

    @Test
    fun `kgp version used in buildSrc clashes with what is expected by kotlin-dsl plugin`() {
        val differentKotlinVersion = KotlinGradlePluginVersions().latests.last {
            VersionNumber.parse(it) != VersionNumber.parse(embeddedKotlinVersion)
        }

        withFile(
            "buildSrc/build.gradle.kts",
            """
                repositories {
                    mavenCentral()
                }
                
                dependencies {
                    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$differentKotlinVersion")
                }
            """
        )

        withDefaultSettings()

        withBuildScript(
            """
                plugins {
                    `kotlin-dsl`
                }
            """
        )

        val result = build("build")
        result.assertOutputContains(
            expectedWarningMessage(differentKotlinVersion)
        )
    }

    private fun expectedWarningMessage(kotlinVersion: String): String =
        """|WARNING: Unsupported Kotlin plugin version.
           |The `embedded-kotlin` and `kotlin-dsl` plugins rely on features of Kotlin `$embeddedKotlinVersion` that might work differently than in the requested version `$kotlinVersion`.
           |Using the `kotlin-dsl` plugin together with a different Kotlin version (for example, by using the Kotlin Gradle plugin (`kotlin(jvm)`)) in the same module is not recommended.
           |See https://kotl.in/gradle/kotlin-dsl-version-incompatibility for more details.""".trimMargin()
}