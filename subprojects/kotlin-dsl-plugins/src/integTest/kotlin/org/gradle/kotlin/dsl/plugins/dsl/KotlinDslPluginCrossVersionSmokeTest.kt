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

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


/**
 * Assert that the cross-version protocol between `:kotlin-dsl-plugins` and `:kotlin-dsl-provider-plugins` is not broken.
 */
class KotlinDslPluginCrossVersionSmokeTest : AbstractKotlinIntegrationTest() {

    @Test
    fun `can run with first version of kotlin-dsl plugin supporting Gradle 8_0`() {

        assumeNonEmbeddedGradleExecuter()
        executer.noDeprecationChecks()

        // Previous versions depend on Kotlin that is not supported with Gradle >= 8.0
        val testedVersion = "3.2.4"
        val blessedVersion = org.gradle.kotlin.dsl.support.expectedKotlinDslPluginsVersion

        withDefaultSettingsIn("buildSrc")
        withBuildScriptIn("buildSrc", scriptWithKotlinDslPlugin(testedVersion)).appendText(
            """
            tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
                kotlinOptions.freeCompilerArgs += "-Xskip-metadata-version-check"
            }
            """
        )
        withFile("buildSrc/src/main/kotlin/some.gradle.kts", """println("some!")""")

        withDefaultSettings()
        withBuildScript("""plugins { id("some") }""")

        build("help").apply {

            assertThat(
                output,
                containsString("This version of Gradle expects version '$blessedVersion' of the `kotlin-dsl` plugin but version '$testedVersion' has been applied to project ':buildSrc'. Let Gradle control the version of `kotlin-dsl` by removing any explicit `kotlin-dsl` version constraints from your build logic.")
            )

            assertThat(
                output,
                containsString("some!")
            )
        }
    }
}
