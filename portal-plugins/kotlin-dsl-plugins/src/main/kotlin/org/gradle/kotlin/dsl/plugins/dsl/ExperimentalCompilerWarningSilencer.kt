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
class ExperimentalCompilerWarningSilencer(private val warningsToSilence: List<String>) : ContextAwareTaskLogger.MessageRewriter {

    private
    val unsafeCompilerArgumentsWarningHeader = "This build uses unsafe internal compiler arguments:"

    override fun rewrite(logLevel: LogLevel, message: String): String? {
        return if (containsWarningsToBeSilenced(logLevel, message)) {
            rewriteMessage(message)
        } else {
            message
        }
    }

    private
    fun rewriteMessage(message: String): String? {
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
    fun containsWarningsToBeSilenced(logLevel: LogLevel, message: String): Boolean {
        if (logLevel != LogLevel.WARN && logLevel != LogLevel.ERROR) {
            return false
        }
        if (!message.contains(unsafeCompilerArgumentsWarningHeader)) {
            return false
        }
        return true
    }
}
