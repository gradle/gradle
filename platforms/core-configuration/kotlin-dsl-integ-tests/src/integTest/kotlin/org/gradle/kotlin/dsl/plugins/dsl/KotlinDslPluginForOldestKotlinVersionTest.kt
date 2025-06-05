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

package org.gradle.kotlin.dsl.plugins.dsl

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

/**
 * Assert that the latest kotlin-dsl plugin can be used with an old Kotlin language version.
 */
class KotlinDslPluginForOldestKotlinVersionTest : AbstractKotlinIntegrationTest() {

    private val oldestKotlinLanguageVersion = "1.6"

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
            executer.expectExternalDeprecatedMessage("w: Language version $oldestKotlinLanguageVersion is deprecated and its support will be removed in a future version of Kotlin")
        }

        build("help").apply {
            assertThat(output, containsString("some!"))
        }
    }
}
