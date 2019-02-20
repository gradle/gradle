package org.gradle.kotlin.dsl.plugins.precompiled

import org.gradle.kotlin.dsl.fixtures.TestWithTempFiles

import org.gradle.kotlin.dsl.plugins.precompiled.tasks.writeScriptPluginAdapterTo

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.startsWith
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

import java.io.File


class PrecompiledScriptPluginTest : TestWithTempFiles() {

    @Test
    fun `plugin id is derived from script file name`() {

        val script =
            newFile("my-script.gradle.kts")

        assertThat(
            PrecompiledScriptPlugin(script).id,
            equalTo("my-script"))
    }

    @Test
    fun `plugin id is prefixed by package name if present`() {

        val script =
            newFile("my-script.gradle.kts", """

                package org.acme

            """)

        assertThat(
            PrecompiledScriptPlugin(script).id,
            equalTo("org.acme.my-script"))
    }

    @Test
    fun `implementationClass is a valid Java identifier`() {

        assertThat(
            "Invalid Java identifier characters are escaped",
            implementationClassForScriptNamed("42-my-script.with 8 invalid characters.gradle.kts"),
            equalTo("_42MyScript_with_8_invalid_charactersPlugin")
        )

        assertThat(
            "Invalid starting character is escaped once",
            implementationClassForScriptNamed(" .gradle.kts"),
            equalTo("_Plugin")
        )
    }

    private
    fun implementationClassForScriptNamed(fileName: String) =
        PrecompiledScriptPlugin(newFile(fileName)).implementationClass

    @Test
    fun `plugin adapter is written to package sub-dir and starts with correct package declaration`() {

        val script =
            newFile("my-script.gradle.kts", """

                package org.acme

            """)

        val outputDir =
            root.resolve("output")

        PrecompiledScriptPlugin(script)
            .writeScriptPluginAdapterTo(outputDir)

        val expectedFile =
            outputDir.resolve("org/acme/MyScriptPlugin.kt")

        assertThat(
            firstNonBlankLineOf(expectedFile),
            equalTo("package org.acme"))
    }

    @Test
    fun `given no package declaration, plugin adapter is written directly to output dir`() {

        val script =
            newFile("my-script.gradle.kts")

        val outputDir =
            root.resolve("output").apply { mkdir() }

        PrecompiledScriptPlugin(script)
            .writeScriptPluginAdapterTo(outputDir)

        val expectedFile =
            outputDir.resolve("MyScriptPlugin.kt")

        assertThat(
            expectedFile.readText(),
            startsWith("""
                /**
                 * Precompiled [my-script.gradle.kts][My_script_gradle] script plugin.
                 *
                 * @see My_script_gradle
                 */
                class MyScriptPlugin
            """.trimIndent()))
    }

    @Test
    fun `can extract package name from script with Windows line endings`() {

        val script =
            newFile("my-script.gradle.kts", "/*\r\n */\r\npackage org.acme\r\n")

        assertThat(
            PrecompiledScriptPlugin(script).packageName,
            equalTo("org.acme"))
    }

    private
    fun firstNonBlankLineOf(expectedFile: File) =
        expectedFile.bufferedReader().useLines {
            it.first { it.isNotBlank() }
        }
}
