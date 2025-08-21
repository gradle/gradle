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

@file:Suppress("FunctionName")

package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import kotlin.test.Test
import kotlin.test.assertTrue

class KotlinDslKgpMismatchIntegrationTest : AbstractKotlinIntegrationTest() { // TODO: move it into kotlin-dsl-plugins ?

    @Test
    fun `kgp and kotlin-dsl plugins applied together with clashing versions`() {
        val differentKotlinVersion = "2.2.10"

        assertTrue {
            embeddedKotlinVersion < differentKotlinVersion
            // has to be a higher version than what's embedded by gradle, otherwise the `kotlin-dsl` plugin will win out
        }

        withBuildScript(
            """
                plugins {
                    kotlin("jvm") version "$differentKotlinVersion"
                    `kotlin-dsl`
                }
            """
        )

        disableStackTraceDetection()
        val result = build("build")
        result.assertOutputContains(
            expectedWarningMessage(differentKotlinVersion)
        )
    }

    @Test
    fun `kgp version used in buildSrc clashes with what is expected by kotlin-dsl plugin`() {
        val differentKotlinVersion = "2.0.21"

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

        disableStackTraceDetection()
        val result = buildAndFail("build")
        result.assertOutputContains(
            expectedWarningMessage(differentKotlinVersion)
        )
    }

    private fun expectedWarningMessage(kotlinVersion: String): String =
        """|WARNING: Unsupported Kotlin plugin version.
           |The `embedded-kotlin` and `kotlin-dsl` plugins rely on features of Kotlin `$embeddedKotlinVersion` that might work differently than in the requested version `$kotlinVersion`.
           |
           |Using the `kotlin-dsl` plugin together with a different Kotlin version (for example, by using the Kotlin Gradle plugin (`kotlin(jvm)`)) in the same module is not recommended.
           |
           |If you are writing a convention plugin (or a precompiled script plugin in general), it's recommended to use only the `kotlin-dsl` plugin and use the embedded Kotlin version. If you are writing a binary plugin, it's recommended that you do not use the `kotlin-dsl` plugin and use the Kotlin Gradle plugin (`kotlin(jvm)`).
           |
           |You can configure your module to use, for example, 2.1, by overriding `kotlin-dsl` behavior using the following snippet:
           |
           |    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
           |        compilerOptions {
           |            apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)
           |            languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)
           |        }
           |    }
           |
           |Note that this may lead to some hard to predict behavior and in general should be avoided, see documentation for more information.
           |
           |Solutions:
           |    • Do not use `kotlin(jvm)` explicitly in this module and allow `kotlin-dsl` to auto-provide a compatible version on its own. If you prefer staying explicit, consider using the `embeddedKotlinVersion` constant.
           |    • Do not use `kotlin-dsl` in this module.
           |    • Configure your module to override `kotlin-dsl` behavior, as shown above.
           |
           |See https://kotl.in/gradle/kotlin-dsl-version-incompatibility for more details.""".trimMargin()

}

private
fun KotlinDslKgpMismatchIntegrationTest.disableStackTraceDetection() {
    executer.withStackTraceChecksDisabled()

    // the code snippet in the warning is incorrectly interpreted as a stack trace by our testing framework
    // OutputScrapingExecutionResult.STACK_TRACE_ELEMENT is wrong, and I'm not going to touch it with a 10-foot pole ...
}