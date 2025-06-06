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

package org.gradle.kotlin.dsl.compile

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.kotlin.dsl.fixtures.clickableUrlFor
import org.hamcrest.CoreMatchers.containsString
import kotlin.test.Test
import kotlin.test.assertTrue

@Suppress("FunctionNaming")
class DeprecationInAccessorsIntegrationTest : AbstractKotlinIntegrationTest() {
    private val pluginId = "my-plugin"

    private val usageScript = """
        plugins {
            id("$pluginId")
        }

        myExtension { }
        tasks {
            myTask { }
        }
    """


    @Test
    fun `if an extension type is deprecated in Java, suppresses deprecation and deprecates the accessors`() {
        withFile(
            "buildSrc/src/main/java/com/example/DeprecatedJavaExt.java", """
            package com.example;

            @Deprecated
            public class DeprecatedJavaExt { }
        """.trimIndent()
        )

        withFile(
            "buildSrc/src/main/java/com/example/DeprecatedJavaTask.java", """
            package com.example;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.TaskAction;

            @Deprecated
            public class DeprecatedJavaTask extends DefaultTask {
                @TaskAction
                public void doNothing() { }
            }
        """.trimIndent()
        )

        withPrecompiledScriptPluginInBuildSrc(
            pluginId,
            """
                import com.example.DeprecatedJavaExt
                import com.example.DeprecatedJavaTask

                @Suppress("deprecation")
                extensions.add("myExtension", DeprecatedJavaExt())

                @Suppress("deprecation")
                tasks.register("myTask", DeprecatedJavaTask::class)
            """
        )
        withBuildScript(usageScript)

        build("kotlinDslAccessorsReport").apply {
            assertOutputContains("build.gradle.kts:6:9: 'fun Project.myExtension(configure: Action<DeprecatedJavaExt>): Unit' is deprecated. Deprecated in Java.")

            assertOutputContains(
                """
                |    @Suppress("deprecation")
                |    @Deprecated("Deprecated in Java", level = DeprecationLevel.WARNING)
                |    val org.gradle.api.Project.`myExtension`: com.example.DeprecatedJavaExt get()
                """.trimMargin()
            )

            assertOutputContains(
                """
                |    @Suppress("deprecation")
                |    @Deprecated("Deprecated in Java", level = DeprecationLevel.WARNING)
                |    fun org.gradle.api.Project.`myExtension`(configure: Action<com.example.DeprecatedJavaExt>): Unit =
                """.trimMargin()
            )

            assertOutputContains(
                """
                |    @Suppress("deprecation")
                |    @Deprecated("Deprecated in Java", level = DeprecationLevel.WARNING)
                |    val TaskContainer.`myTask`: TaskProvider<com.example.DeprecatedJavaTask>
                """.trimMargin()
            )
        }
    }

    private fun deprecatedKotlinScriptPlugin(deprecationLevel: DeprecationLevel): String {
        val level = deprecationLevel.name
        val suppression = when (deprecationLevel) {
            DeprecationLevel.ERROR -> "DEPRECATION_ERROR"
            DeprecationLevel.WARNING -> "deprecation"
            DeprecationLevel.HIDDEN -> ""
        }

        val extConstructionExpr = when (deprecationLevel) {
            DeprecationLevel.HIDDEN -> "Class.forName(\"My_plugin_gradle\\${'$'}DeprecatedExt\").kotlin.constructors.single().call()"
            else -> "DeprecatedExt()"
        }

        val taskClassExpr = when (deprecationLevel) {
            DeprecationLevel.HIDDEN -> "Class.forName(\"My_plugin_gradle\\${'$'}DeprecatedTask\")"
            else -> "DeprecatedTask::class"
        }

        val typeArgsForTaskRegister = when (deprecationLevel) {
            DeprecationLevel.HIDDEN -> "<DefaultTask>"
            else -> ""
        }

        return """
            @Deprecated("Just don't", level = DeprecationLevel.$level)
            class DeprecatedExt

            @Deprecated("Just don't", level = DeprecationLevel.$level)
            class DeprecatedTask : DefaultTask() {
                @TaskAction
                fun doNothing() = Unit
            }

            @Suppress("$suppression")
            extensions.add("myExtension", $extConstructionExpr)

            @Suppress("$suppression")
            tasks.register$typeArgsForTaskRegister("myTask", $taskClassExpr)
        """
    }


    @Test
    fun `if an extension type is deprecated in Kotlin, suppresses deprecation and deprecates the accessors`() {
        withPrecompiledScriptPluginInBuildSrc(
            pluginId,
            deprecatedKotlinScriptPlugin(DeprecationLevel.WARNING)
        )
        withBuildScript(usageScript)

        build(":kotlinDslAccessorsReport").apply {
            assertTrue {
                this.output.lines().any {
                    it.startsWith("w: ") &&
                        it.endsWith("build.gradle.kts:6:9: 'fun Project.myExtension(configure: Action<My_plugin_gradle.DeprecatedExt>): Unit' is deprecated. Just don't.")
                }
            }
            assertTrue {
                this.output.lines().any {
                    it.startsWith("w: ") &&
                        it.endsWith("build.gradle.kts:8:13: 'val TaskContainer.myTask: TaskProvider<My_plugin_gradle.DeprecatedTask>' is deprecated. Just don't.")
                }
            }

            assertOutputContains(
                """
                |    @Suppress("deprecation")
                |    @Deprecated("Just don't", level = DeprecationLevel.WARNING)
                |    val org.gradle.api.Project.`myExtension`: My_plugin_gradle.DeprecatedExt get()
                """.trimMargin()
            )

            assertOutputContains(
                """
                |    @Suppress("deprecation")
                |    @Deprecated("Just don't", level = DeprecationLevel.WARNING)
                |    fun org.gradle.api.Project.`myExtension`(configure: Action<My_plugin_gradle.DeprecatedExt>): Unit =
                """.trimMargin()
            )

            assertOutputContains(
                """
                |    @Suppress("deprecation")
                |    @Deprecated("Just don't", level = DeprecationLevel.WARNING)
                |    val TaskContainer.`myTask`: TaskProvider<My_plugin_gradle.DeprecatedTask>
                """.trimMargin()
            )
        }
    }

    @Test
    fun `if an extension type is deprecated as error in Kotlin, suppresses deprecation and makes the accessors deprecated`() {
        withPrecompiledScriptPluginInBuildSrc(
            pluginId,
            deprecatedKotlinScriptPlugin(DeprecationLevel.ERROR)
        )
        withBuildScript(usageScript)

        val extDeprecationLine = "'fun Project.myExtension(configure: Action<My_plugin_gradle.DeprecatedExt>): Unit' is deprecated. Just don't."
        val taskDeprecationLine = "'val TaskContainer.myTask: TaskProvider<My_plugin_gradle.DeprecatedTask>' is deprecated. Just don't."

        executer.expectExternalDeprecatedMessage("e: ${clickableUrlFor(file("build.gradle.kts"))}:6:9: $extDeprecationLine")
        executer.expectExternalDeprecatedMessage("e: ${clickableUrlFor(file("build.gradle.kts"))}:8:13: $taskDeprecationLine")
        executer.expectExternalDeprecatedMessage("                  ^ $extDeprecationLine") // compiler error details
        executer.expectExternalDeprecatedMessage("                      ^ $taskDeprecationLine") // compiler error details

        buildAndFail("help").apply {
            assertThatDescription(containsString("Script compilation error"))
            assertThatDescription(containsString(taskDeprecationLine))
        }
    }

    @Test
    fun `if an extension type is deprecated as hidden in Kotlin, generates accessors for inaccessible types`() {
        withPrecompiledScriptPluginInBuildSrc(
            pluginId,
            deprecatedKotlinScriptPlugin(DeprecationLevel.HIDDEN)
        )
        withBuildScript("""
            plugins { id("$pluginId") }

            println("Has non-empty build file")
        """)

        repeat(2) {
            executer.expectExternalDeprecatedMessage("     * - `My_plugin_gradle.DeprecatedExt` is deprecated as hidden")
        }

        build(":kotlinDslAccessorsReport").apply {
            assertOutputContains(
                """
                |     * `myExtension` is not accessible in a type safe way because:
                |     * - `My_plugin_gradle.DeprecatedExt` is deprecated as hidden
                |     */
                |    val org.gradle.api.Project.`myExtension`: Any
                """.trimMargin()
            )
            assertOutputContains(
                """
                |     * `myExtension` is not accessible in a type safe way because:
                |     * - `My_plugin_gradle.DeprecatedExt` is deprecated as hidden
                |     */
                |    fun org.gradle.api.Project.`myExtension`(configure: Action<Any>): Unit =
                """.trimMargin()
            )
        }
    }


    private fun withPrecompiledScriptPluginInBuildSrc(pluginId: String, pluginSource: String) {
        withBuildScriptIn("buildSrc", scriptWithKotlinDslPlugin())
        withFile("buildSrc/src/main/kotlin/$pluginId.gradle.kts", pluginSource)
    }
}
