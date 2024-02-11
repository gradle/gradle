package org.gradle.kotlin.dsl.provider.plugins.precompiled

import org.gradle.kotlin.dsl.fixtures.TestWithTempFiles

import org.gradle.kotlin.dsl.provider.plugins.precompiled.tasks.writeScriptPluginAdapterTo

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.startsWith
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test
import spock.lang.Issue

import java.io.File


class PrecompiledScriptPluginTest : TestWithTempFiles() {

    @Test
    fun `plugin id is derived from script file name`() {

        assertThat(
            scriptPlugin("my-script.gradle.kts").id,
            equalTo("my-script")
        )
    }

    @Test
    fun `plugin id can contain dot gradle dot kts substring within its id`() {

        assertThat(
            scriptPlugin("dev.gradle.ktscript.plugin.gradle.kts").id,
            equalTo("dev.gradle.ktscript.plugin")
        )
    }

    @Test
    fun `plugin id is prefixed by package name if present`() {

        assertThat(
            scriptPlugin(
                "my-script.gradle.kts",
                """

                    package org.acme

                """
            ).id,
            equalTo("org.acme.my-script")
        )
    }

    @Issue("https://github.com/gradle/gradle/issues/16154")
    @Test
    fun `package name detection works even when there is a shebang line and file annotations`() {

        assertThat(
            scriptPlugin(
                "my-script.gradle.kts",
                """

                    // she-bangs! ///////_*&@ because why not! _-|

                    #!/something/something


                    /* first file annotation */
                    @file:Suppress("UnstableApiUsage")

                    // second file annotation //second comment, just for fun

                    @file    :    [   SuppressWarnings    Incubating   Suppress(
                        "unused",
                        "nothing_to_inline"
                    )    ]

                    /* /* one more weird comment here */ */

                    package org.acme; // semicolon intentionally added to make sure it doesn't mess with processing

                """
            ).id,
            equalTo("org.acme.my-script")
        )
    }

    @Test
    fun `package name detection works when there is none`() {

        assertThat(
            scriptPlugin(
                "my-script.gradle.kts",
                """

                    @file:Suppress("UnstableApiUsage") // using a file annotation to disable simple package detection

                    //import org.test.Message as TestMessage

                    //val a = 1

                    plugins {}
                """
            ).id,
            equalTo("my-script")
        )
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
        scriptPlugin(fileName).implementationClass

    @Test
    fun `plugin adapter is written to package sub-dir and starts with correct package declaration`() {

        val outputDir =
            root.resolve("output")

        scriptPlugin(
            "my-script.gradle.kts",
            """

                package org.acme

            """
        ).writeScriptPluginAdapterTo(outputDir)

        val expectedFile =
            outputDir.resolve("org/acme/MyScriptPlugin.kt")

        assertThat(
            firstNonBlankLineOf(expectedFile),
            equalTo("package org.acme")
        )
    }

    @Test
    fun `given no package declaration, plugin adapter is written directly to output dir`() {

        val outputDir =
            root.resolve("output").apply { mkdir() }

        scriptPlugin("my-script.gradle.kts")
            .writeScriptPluginAdapterTo(outputDir)

        val expectedFile =
            outputDir.resolve("MyScriptPlugin.kt")

        assertThat(
            expectedFile.readText(),
            startsWith(
                """
                /**
                 * Precompiled [my-script.gradle.kts][My_script_gradle] script plugin.
                 *
                 * @see My_script_gradle
                 */
                public
                class MyScriptPlugin
                """.trimIndent()
            )
        )
    }

    @Test
    fun `can extract package name from script with Windows line endings`() {

        assertThat(
            scriptPlugin(
                "my-script.gradle.kts",
                "/*\r\n */\r\npackage org.acme\r\n"
            ).packageName,
            equalTo("org.acme")
        )
    }

    private
    fun scriptPlugin(fileName: String, text: String = "") = PrecompiledScriptPlugin(newFile(fileName, text))

    private
    fun firstNonBlankLineOf(expectedFile: File) =
        expectedFile.bufferedReader().useLines {
            it.first { it.isNotBlank() }
        }
}
