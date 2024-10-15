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

package org.gradle.internal.cc.impl

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.internal.cc.base.exceptions.ConfigurationCacheException

open class ConfigurationCacheProblemsException : ConfigurationCacheException {

    protected
    object Documentation {

        val maxProblems: String
            get() = DocumentationRegistry().getDocumentationRecommendationFor("on this", "configuration_cache", "config_cache:usage:max_problems")
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
        "This behavior can be adjusted. ${Documentation.maxProblems}",
    causes,
    summary
)
