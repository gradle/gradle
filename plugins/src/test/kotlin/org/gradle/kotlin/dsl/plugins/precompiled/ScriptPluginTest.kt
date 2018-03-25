package org.gradle.kotlin.dsl.plugins.precompiled

import org.gradle.kotlin.dsl.fixtures.TestWithTempFiles

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.startsWith
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

import java.io.File


class ScriptPluginTest : TestWithTempFiles() {

    @Test
    fun `plugin id is derived from script file name`() {

        val script =
            newFile("my-script.gradle.kts")

        assertThat(
            ScriptPlugin(script).id,
            equalTo("my-script"))
    }

    @Test
    fun `plugin id is prefixed by package name if present`() {

        val script =
            newFile("my-script.gradle.kts", """

                package org.acme

            """)

        assertThat(
            ScriptPlugin(script).id,
            equalTo("org.acme.my-script"))
    }

    @Test
    fun `implementationClass is a valid Java identifier`() {

        val script =
            newFile("my-script.with invalid characters.gradle.kts")

        assertThat(
            ScriptPlugin(script).implementationClass,
            equalTo("MyScript_with_invalid_charactersPlugin"))
    }

    @Test
    fun `plugin adapter is written to package sub-dir and starts with correct package declaration`() {

        val script =
            newFile("my-script.gradle.kts", """

                package org.acme

            """)

        val outputDir =
            root.resolve("output")

        ScriptPlugin(script)
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

        ScriptPlugin(script)
            .writeScriptPluginAdapterTo(outputDir)

        val expectedFile =
            outputDir.resolve("MyScriptPlugin.kt")

        assertThat(
            firstNonBlankLineOf(expectedFile),
            startsWith("class MyScriptPlugin "))
    }

    private
    fun firstNonBlankLineOf(expectedFile: File) =
        expectedFile.bufferedReader().useLines {
            it.first { it.isNotBlank() }
        }
}
