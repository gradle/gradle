/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.internal.code.UserCodeApplicationContext
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.serialization.Cached
import org.gradle.invocation.ConfigurationCacheDegradationController
import java.util.concurrent.ConcurrentHashMap


class DefaultConfigurationCacheDegradationController(
    private val userCodeApplicationContext: UserCodeApplicationContext
) : ConfigurationCacheDegradationController {

    private val degradationRequests = ConcurrentHashMap.newKeySet<DegradationRequest>()

    override fun requireConfigurationCacheDegradationIf(reason: String, spec: Cached<Boolean>) {
        val trace = userCodeApplicationContext.current()?.let { PropertyTrace.BuildLogic(it.source) }
            ?: PropertyTrace.Unknown
        degradationRequests.add(DegradationRequest(trace, reason, spec))
    }

    fun shouldDegradeWithReasons(): List<String> =
        degradationRequests
            .filter { it.spec.get() }
            .map { it.reason }

    fun shouldDegradeWithReasons(trace: PropertyTrace): List<String> =
        degradationRequests
            .filter { it.trace == trace && it.spec.get() }
            .map { it.reason }

    private data class DegradationRequest(
        val trace: PropertyTrace,
        val reason: String,
        val spec: Cached<Boolean>
    )
}
