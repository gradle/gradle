/*
 * Copyright 2023 the original author or authors.
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
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.junit.Test


class KotlinDslPluginCustomKotlinOptionsIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    @Requires(IntegTestPreconditions.NotEmbeddedExecutor::class)
    fun `can configure custom kotlin options on a kotlin-dsl project`() {

        withDefaultSettingsIn("buildSrc")
        val buildSrcBuildScript = withKotlinDslPluginIn("buildSrc")
        withFile(
            "buildSrc/src/main/kotlin/MyDataObject.kt",
            """
            data object MyDataObject {
                val some = "content"
                val other: String = ${'$'}${'$'}""${'"'}${'$'}some = ${'$'}${'$'}some""${'"'}
            }
            """.trimIndent()
        )
        withBuildScript("println(MyDataObject.other)")
        buildAndFail("help").apply {
            assertHasErrorOutput("""The feature "multi dollar interpolation" is experimental and should be enabled explicitly""")
        }

        buildSrcBuildScript.appendText("""
            tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
                compilerOptions {
                    apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)
                    languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)
                    freeCompilerArgs.add("-Xmulti-dollar-interpolation")
                }
            }
        """)
        build("help").apply {
            assertOutputContains("${'$'}some = content")
        }
    }
}
