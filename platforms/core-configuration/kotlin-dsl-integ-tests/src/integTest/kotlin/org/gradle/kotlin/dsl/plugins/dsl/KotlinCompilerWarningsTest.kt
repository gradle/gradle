/*
 * Copyright 2021 the original author or authors.
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
import org.junit.Test


class KotlinCompilerWarningsTest : AbstractKotlinIntegrationTest() {

    private
    val experimentalWarningHeader = "This build uses unsafe internal compiler arguments"

    private
    val experimentalFeatureWithoutWarning = "-XXLanguage:+DisableCompatibilityModeForNewInference"

    private
    val experimentalFeatureToWarnAbout = "-XXLanguage:+FunctionReferenceWithDefaultValueAsOtherType"

    @Test
    @Requires(IntegTestPreconditions.NotEmbeddedExecutor::class)
    fun `experimental compiler warnings are not shown for known experimental features`() {
        withBuildScriptForKotlinCompile()
        withKotlinSourceFile()

        val result = build("compileKotlin")

        result.assertNotOutput(experimentalWarningHeader)
        result.assertNotOutput(experimentalFeatureWithoutWarning)
    }

    @Test
    @Requires(IntegTestPreconditions.NotEmbeddedExecutor::class)
    fun `experimental compiler warnings are not shown for known experimental features with allWarningsAsErrors`() {
        withBuildScriptForKotlinCompile("allWarningsAsErrors.set(true)")
        withKotlinSourceFile()

        val result = build("compileKotlin")

        result.assertNotOutput(experimentalWarningHeader)
        result.assertNotOutput(experimentalFeatureWithoutWarning)
    }


    @Test
    @Requires(IntegTestPreconditions.NotEmbeddedExecutor::class)
    fun `compileKotlin task output is retained when known experimental feature warnings are silenced`() {
        withBuildScriptForKotlinCompile(
            "",
            """
            doFirst {
                println("before compiling")
            }
            doLast {
                println("after compiling")
            }
        """
        )
        withKotlinSourceFile()

        val result = build("compileKotlin")

        result.assertOutputContains("before compiling")
        result.assertOutputContains("after compiling")
        result.assertNotOutput(experimentalWarningHeader)
        result.assertNotOutput(experimentalFeatureWithoutWarning)
    }

    @Test
    @Requires(IntegTestPreconditions.NotEmbeddedExecutor::class)
    fun `compiler warning for an explicitly enabled experimental feature is shown`() {
        withBuildScriptForKotlinCompile("freeCompilerArgs.add(\"$experimentalFeatureToWarnAbout\")")
        withKotlinSourceFile()

        val result = build("compileKotlin")

        result.assertOutputContains(experimentalWarningHeader)
        result.assertOutputContains(experimentalFeatureToWarnAbout)
        result.assertNotOutput(experimentalFeatureWithoutWarning)
    }

    @Test
    @Requires(IntegTestPreconditions.NotEmbeddedExecutor::class)
    fun `compiler warning for an explicitly enabled experimental feature is shown with allWarningsAsErrors`() {
        withBuildScriptForKotlinCompile(
            """
            allWarningsAsErrors.set(true)
            freeCompilerArgs.add("$experimentalFeatureToWarnAbout")
        """
        )
        withKotlinSourceFile()

        val result = build("compileKotlin")

        result.assertHasErrorOutput(experimentalWarningHeader)
        result.assertHasErrorOutput(experimentalFeatureToWarnAbout)
        result.assertNotOutput(experimentalFeatureWithoutWarning)
    }

    private
    fun withBuildScriptForKotlinCompile(compilerOptions: String = "", kotlinTaskConfig: String = "") {
        var compileKotlinTaskConfiguration = ""
        if (compilerOptions.isNotEmpty() || kotlinTaskConfig.isNotEmpty()) {
            compileKotlinTaskConfiguration = """
                tasks.withType<KotlinCompile>().configureEach {
                    $kotlinTaskConfig
                    compilerOptions {
                        $compilerOptions
                    }
                }
            """
        }

        withBuildScript(
            """
            import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
            plugins {
                `kotlin-dsl`
            }
            $repositoriesBlock
            $compileKotlinTaskConfiguration
            """
        )
    }

    private
    fun withKotlinSourceFile() {
        withFile(
            "src/main/kotlin/my/Foo.kt",
            """
            package my

            class Foo(val bar: String)
            """
        )
    }
}
