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

import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.slf4j.ContextAwareTaskLogger


internal
class ExperimentalCompilerWarningSilencer(

    private
    val warningsToSilence: List<String>

) : ContextAwareTaskLogger.MessageRewriter {

    private
    val rewrittenLevels = listOf(LogLevel.WARN, LogLevel.ERROR)

    private
    val unsafeCompilerArgumentsWarningHeader = "This build uses unsafe internal compiler arguments:"

    override fun rewrite(logLevel: LogLevel, message: String): String? =
        if (logLevel in rewrittenLevels) rewriteMessage(message)
        else message

    private
    fun rewriteMessage(message: String) =
        if (message.contains(unsafeCompilerArgumentsWarningHeader)) rewriteUnsafeCompilerArgumentsWarning(message)
        else if (message.containsSilencedWarning()) null
        else message

    private
    fun rewriteUnsafeCompilerArgumentsWarning(message: String): String? {
        var rewrittenMessage = message
        for (warning in warningsToSilence) {
            rewrittenMessage = rewrittenMessage.replace("$warning\n", "")
        }
        if (rewrittenMessage.lines().any { it.startsWith("-") }) {
            return rewrittenMessage
        }
        return null
    }

    private
    fun String.containsSilencedWarning() =
        warningsToSilence.any { contains(it) }
}
