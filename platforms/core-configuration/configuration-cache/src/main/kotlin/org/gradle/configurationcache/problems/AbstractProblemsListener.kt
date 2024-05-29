/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.configurationcache.problems

import org.gradle.configurationcache.ConfigurationCacheError
import org.gradle.configurationcache.ConfigurationCacheThrowable
import org.gradle.internal.extensions.stdlib.maybeUnwrapInvocationTargetException
import org.gradle.internal.configuration.problems.ProblemsListener
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.configuration.problems.StructuredMessage
import org.gradle.internal.configuration.problems.StructuredMessageBuilder
import java.io.IOException


abstract class AbstractProblemsListener : ProblemsListener {

    override fun onError(trace: PropertyTrace, error: Exception, message: StructuredMessageBuilder) {
        // Let IO and configuration cache exceptions surface to the top.
        if (error is IOException || error is ConfigurationCacheThrowable) {
            throw error
        }
        throw ConfigurationCacheError(
            "Configuration cache state could not be cached: $trace: ${StructuredMessage.build(message)}",
            error.maybeUnwrapInvocationTargetException()
        )
    }

    override fun forIncompatibleTask(path: String): ProblemsListener = this
}
