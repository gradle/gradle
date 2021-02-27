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

import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.logging.LogLevel
import org.junit.Test


class ExperimentalCompilerWarningSilencerTest {

    private
    val warningToSilence = "-XXLanguage:+DisableCompatibilityModeForNewInference"

    private
    val silencer = ExperimentalCompilerWarningSilencer(listOf(warningToSilence))

    @Test
    fun `returns log message as-is when it does not contain warning to silence`() {
        val rewrittenMessage = silencer.rewrite(LogLevel.WARN, "foo")

        assertThat(rewrittenMessage).isEqualTo("foo")
    }

    @Test
    fun `silences experimental compiler warning`() {
        val rewrittenMessage = silencer.rewrite(
            LogLevel.WARN,
            """
            This build uses unsafe internal compiler arguments:

            $warningToSilence

            This mode is not recommended for production use,
            as no stability/compatibility guarantees are given on
            compiler or generated code. Use it at your own risk!
            """.trimIndent()
        )

        assertThat(rewrittenMessage).isNull()
    }

    @Test
    fun `silences requested experimental compiler warning and preserves the rest of warnings`() {
        val rewrittenMessage = silencer.rewrite(
            LogLevel.WARN,
            """
            This build uses unsafe internal compiler arguments:

            $warningToSilence
            -XXLanguage:+FunctionReferenceWithDefaultValueAsOtherType

            This mode is not recommended for production use,
            as no stability/compatibility guarantees are given on
            compiler or generated code. Use it at your own risk!
            """.trimIndent()
        )

        assertThat(rewrittenMessage).isEqualTo(
            """
            This build uses unsafe internal compiler arguments:

            -XXLanguage:+FunctionReferenceWithDefaultValueAsOtherType

            This mode is not recommended for production use,
            as no stability/compatibility guarantees are given on
            compiler or generated code. Use it at your own risk!
            """.trimIndent()
        )
    }

    // this happens sometimes when for some reason incremental compilation fails with warning:
    // Could not perform incremental compilation: Could not connect to Kotlin compile daemon
    // Could not connect to kotlin daemon. Using fallback strategy.
    @Test
    fun `silences experimental compiler warning when warning header is missing`() {
        val rewrittenMessage = silencer.rewrite(
            LogLevel.WARN,
            """

            $warningToSilence

            This mode is not recommended for production use,
            as no stability/compatibility guarantees are given on
            compiler or generated code. Use it at your own risk!
            """.trimIndent()
        )

        assertThat(rewrittenMessage).isNull()
    }
}
