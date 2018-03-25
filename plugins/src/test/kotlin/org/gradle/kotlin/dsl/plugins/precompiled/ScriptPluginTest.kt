package org.gradle.kotlin.dsl.plugins.precompiled

import org.gradle.kotlin.dsl.fixtures.TestWithTempFiles

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test


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

        assertThat(
            ScriptPlugin(newFile("my-script.with invalid characters.gradle.kts")).implementationClass,
            equalTo("MyScript_with_invalid_charactersPlugin"))
    }
}
