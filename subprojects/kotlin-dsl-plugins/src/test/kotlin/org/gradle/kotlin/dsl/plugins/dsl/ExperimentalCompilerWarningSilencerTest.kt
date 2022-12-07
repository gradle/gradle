/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.api.logging.LogLevel
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.Test


class ExperimentalCompilerWarningSilencerTest {

    @Test
    fun `does not tamper regular message`() {
        val silencer = ExperimentalCompilerWarningSilencer(listOf("SOME"))

        val message = "Hello, World!"
        val rewritten = silencer.rewrite(LogLevel.WARN, message)

        assertThat(rewritten, equalTo(message))
    }

    @Test
    fun `silences unsafe internal compiler arguments`() {
        val silencer = ExperimentalCompilerWarningSilencer(listOf("-XXLanguage:+DisableCompatibilityModeForNewInference"))

        val message = "w: ATTENTION!\n" +
            "This build uses unsafe internal compiler arguments:\n" +
            "\n" +
            "-XXLanguage:+DisableCompatibilityModeForNewInference\n" +
            "\n" +
            "This mode is not recommended for production use,\n" +
            "as no stability/compatibility guarantees are given on\n" +
            "compiler or generated code. Use it at your own risk!\n"
        val rewritten = silencer.rewrite(LogLevel.WARN, message)

        assertThat(rewritten, nullValue())
    }

    @Test
    fun `does not silence unsafe internal compiler arguments`() {
        val silencer = ExperimentalCompilerWarningSilencer(listOf("-XXLanguage:+DisableCompatibilityModeForNewInference"))

        val message = "w: ATTENTION!\n" +
            "This build uses unsafe internal compiler arguments:\n" +
            "\n" +
            "-XXLanguage:+DisableCompatibilityModeForNewInference\n" +
            "-XXLanguage:+FunctionReferenceWithDefaultValueAsOtherType\n" +
            "\n" +
            "This mode is not recommended for production use,\n" +
            "as no stability/compatibility guarantees are given on\n" +
            "compiler or generated code. Use it at your own risk!\n"
        val rewritten = silencer.rewrite(LogLevel.WARN, message)

        val expected = "w: ATTENTION!\n" +
            "This build uses unsafe internal compiler arguments:\n" +
            "\n" +
            "-XXLanguage:+FunctionReferenceWithDefaultValueAsOtherType\n" +
            "\n" +
            "This mode is not recommended for production use,\n" +
            "as no stability/compatibility guarantees are given on\n" +
            "compiler or generated code. Use it at your own risk!\n"
        assertThat(rewritten, equalTo(expected))
    }

    @Test
    fun `silences deprecated compiler flag`() {
        val silencer = ExperimentalCompilerWarningSilencer(listOf("-Xuse-old-backend"))

        val message = "w: -Xuse-old-backend is deprecated and will be removed in a future release"
        val rewritten = silencer.rewrite(LogLevel.WARN, message)

        assertThat(rewritten, nullValue())
    }
}
