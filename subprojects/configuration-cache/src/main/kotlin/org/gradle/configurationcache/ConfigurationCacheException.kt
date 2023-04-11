/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.configurationcache

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.internal.exceptions.Contextual
import org.gradle.internal.exceptions.DefaultMultiCauseException


/**
 * Marker interface for exception handling.
 */
internal
interface ConfigurationCacheThrowable


/**
 * State might be corrupted and should be discarded.
 */
@Contextual
class ConfigurationCacheError internal constructor(
    error: String,
    cause: Throwable? = null
) : ConfigurationCacheThrowable, Exception(error, cause)


@Contextual
sealed class ConfigurationCacheException protected constructor(
    message: () -> String,
    causes: Iterable<Throwable>
) : DefaultMultiCauseException(message, causes), ConfigurationCacheThrowable


open class ConfigurationCacheProblemsException : ConfigurationCacheException {

    protected
    object Documentation {

        val maxProblems: String
            get() = DocumentationRegistry().getDocumentationFor("configuration_cache", "config_cache:usage:max_problems")
    }

    protected
    constructor(
        message: String,
        causes: List<Throwable>,
        summary: () -> String
    ) : super(
        { "$message\n${summary()}" },
        causes
    )

    internal
    constructor(
        causes: List<Throwable>,
        summary: () -> String
    ) : this(
        "Configuration cache problems found in this build.",
        causes,
        summary
    )
}


class TooManyConfigurationCacheProblemsException internal constructor(
    causes: List<Throwable>,
    summary: () -> String
) : ConfigurationCacheProblemsException(
    "Maximum number of configuration cache problems has been reached.\n" +
        "This behavior can be adjusted, see ${Documentation.maxProblems}.",
    causes,
    summary
)
