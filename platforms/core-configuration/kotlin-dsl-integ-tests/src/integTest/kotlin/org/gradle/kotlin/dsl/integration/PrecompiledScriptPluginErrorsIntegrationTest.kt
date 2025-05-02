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

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.util.internal.TextUtil.normaliseFileSeparators
import org.junit.Test
import spock.lang.Issue
import java.io.File


@LeaksFileHandles("Kotlin Compiler Daemon working directory")
class PrecompiledScriptPluginErrorsIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    fun `should not allow precompiled plugin to conflict with core plugin`() {
        withKotlinBuildSrc()
        withFile(
            "buildSrc/src/main/kotlin/java.gradle.kts",
            """
            tasks.register("myTask") {}
            """
        )
        withDefaultSettings()
        withFile(
            "build.gradle",
            """
            plugins {
                java
            }
            """
        )

        buildAndFail("help")
            .assertHasCauseWorkingAroundIssue25636(
                "The precompiled plugin (${
                "src/main/kotlin/java.gradle.kts".replace(
                    "/",
                    File.separator
                )
                }) conflicts with the core plugin 'java'. Rename your plugin."
            )
            .assertHasResolution(getPrecompiledPluginsLink())
    }

    @Test
    fun `should not allow precompiled plugin to have org-dot-gradle prefix`() {
        withKotlinBuildSrc()
        withFile(
            "buildSrc/src/main/kotlin/org.gradle.my-plugin.gradle.kts",
            """
            tasks.register("myTask") {}
            """
        )
        withDefaultSettings()

        buildAndFail("help")
            .assertHasCauseWorkingAroundIssue25636(
                "The precompiled plugin (${
                "src/main/kotlin/org.gradle.my-plugin.gradle.kts".replace(
                    "/",
                    File.separator
                )
                }) cannot start with 'org.gradle' or be in the 'org.gradle' package."
            )
            .assertHasResolution(getPrecompiledPluginsLink())
    }

    @Test
    fun `should not allow precompiled plugin to be in org-dot-gradle package`() {
        withKotlinBuildSrc()
        withFile(
            "buildSrc/src/main/kotlin/org/gradle/my-plugin.gradle.kts",
            """
            package org.gradle

            tasks.register("myTask") {}
            """
        )
        withDefaultSettings()

        buildAndFail("help")
            .assertHasCauseWorkingAroundIssue25636(
                "The precompiled plugin (${
                "src/main/kotlin/org/gradle/my-plugin.gradle.kts".replace(
                    "/",
                    File.separator
                )
                }) cannot start with 'org.gradle' or be in the 'org.gradle' package."
            )
            .assertHasResolution(getPrecompiledPluginsLink())
    }

    private
    fun getPrecompiledPluginsLink(): String = DocumentationRegistry().getDocumentationRecommendationFor("information", "custom_plugins", "sec:precompiled_plugins")


    @Test
    @Issue("https://github.com/gradle/gradle/issues/17831")
    fun `fails with a reasonable error message if init precompiled script has no plugin id`() {
        withKotlinDslPlugin()
        val init = withPrecompiledKotlinScript("init.gradle.kts", "")
        buildAndFail(":compileKotlin").apply {
            assertHasCauseWorkingAroundIssue25636("Precompiled script '${normaliseFileSeparators(init.absolutePath)}' file name is invalid, please rename it to '<plugin-id>.init.gradle.kts'.")
        }
    }

    @Test
    @Issue("https://github.com/gradle/gradle/issues/17831")
    fun `fails with a reasonable error message if settings precompiled script has no plugin id`() {
        withKotlinDslPlugin()
        val settings = withPrecompiledKotlinScript("settings.gradle.kts", "")
        buildAndFail(":compileKotlin").apply {
            assertHasCauseWorkingAroundIssue25636("Precompiled script '${normaliseFileSeparators(settings.absolutePath)}' file name is invalid, please rename it to '<plugin-id>.settings.gradle.kts'.")
        }
    }


    @Issue("https://github.com/gradle/gradle/issues/24788")
    @Test
    @Requires(IntegTestPreconditions.NotEmbeddedExecutor::class)
    fun `fail with a reasonable message when kotlin-dsl plugin compiler arguments have been tempered with`() {

        withKotlinDslPlugin().appendText(
            """
            tasks.compileKotlin {
                compilerOptions {
                    freeCompilerArgs.set(listOf("some"))
                }
            }
            """
        )
        withPrecompiledKotlinScript("some.gradle.kts", "")

        buildAndFail("compileKotlin").apply {
            assertHasFailure("Execution failed for task ':compileKotlin'.") {
                assertHasCause(
                    "Kotlin compiler arguments of task ':compileKotlin' do not work for the `kotlin-dsl` plugin. " +
                        "The 'freeCompilerArgs' property has been reassigned. " +
                        "It must instead be appended to. " +
                        "Please use 'freeCompilerArgs.addAll(\"your\", \"args\")' to fix this."
                )
            }
        }
    }
}


// TODO remove once https://github.com/gradle/gradle/issues/25636 is fixed
private
fun ExecutionFailure.assertHasCauseWorkingAroundIssue25636(cause: String) = run {
    assertHasFailures(error.split(cause).dropLastWhile(String::isEmpty).size - 1)
    assertHasCause(cause)
}
