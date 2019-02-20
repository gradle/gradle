/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.kotlin.dsl.plugins.precompiled.tasks

import org.gradle.kotlin.dsl.fixtures.TestWithTempFiles
import org.gradle.kotlin.dsl.fixtures.equalToMultiLineString

import org.gradle.kotlin.dsl.plugins.precompiled.PrecompiledScriptPlugin

import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.TypeSafeMatcher

import org.junit.Test

import java.io.File


class ExtractPrecompiledScriptPluginPluginsTest : TestWithTempFiles() {

    @Test
    fun `extract plugins blocks and writes them to the output dir`() {

        val scriptPlugins = listOf(

            scriptPlugin("plugins-only.gradle.kts", """
                // this comment will be removed
                plugins {
                    java
                }
                // and so will the rest of the script
            """),

            scriptPlugin("no-plugins.gradle.kts", """
                buildscript {}
            """),

            scriptPlugin("empty-plugins.gradle.kts", """
                plugins {}
            """),

            // the `buildscript` block is not really valid in precompiled script plugins (causes a runtime error)
            // but still worth testing here
            scriptPlugin("buildscript-and-plugins.gradle.kts", """
                buildscript {}
                plugins { java }
            """)
        )

        val outputDir = newFolder("plugins")
        extractPrecompiledScriptPluginPluginsTo(
            outputDir,
            scriptPlugins
        )

        fun outputFile(fileName: String) = outputDir.resolve(fileName)

        assertThat(
            outputFile("plugins-only.gradle.kts").readText(),
            equalToMultiLineString("""
                ${"// this comment will be removed".replacedBySpaces()}
                plugins {
                    java
                }"""
            )
        )

        assertThat(
            outputFile("no-plugins.gradle.kts"),
            doesNotExist()
        )

        assertThat(
            outputFile("empty-plugins.gradle.kts"),
            doesNotExist()
        )

        assertThat(
            outputFile("buildscript-and-plugins.gradle.kts").readText(),
            equalToMultiLineString("""
                ${"buildscript {}".replacedBySpaces()}
                plugins { java }"""
            )
        )
    }

    private
    fun scriptPlugin(fileName: String, text: String) = PrecompiledScriptPlugin(newFile(fileName, text))

    private
    fun String.replacedBySpaces() = repeat(' ', length)
}


private
fun repeat(char: Char, count: Int) = String(CharArray(count) { _ -> char })


private
fun doesNotExist(): Matcher<in File> = object : TypeSafeMatcher<File>() {

    override fun describeTo(description: Description) {
        description.appendText("nonexistent file")
    }

    override fun matchesSafely(file: File): Boolean = !file.exists()
}
