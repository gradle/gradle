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

import org.gradle.api.Task
import org.gradle.api.internal.GeneratedSubclasses
import org.gradle.api.internal.TaskInternal
import org.gradle.api.provider.Provider
import org.gradle.internal.code.UserCodeApplicationContext
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.invocation.ConfigurationCacheDegradationController
import java.util.concurrent.ConcurrentHashMap


class DefaultConfigurationCacheDegradationController(
    private val userCodeApplicationContext: UserCodeApplicationContext
) : ConfigurationCacheDegradationController {

    private val buildLogicDegradationRequests = ConcurrentHashMap.newKeySet<DegradationRequest>()
    private val tasksDegradationRequests = ConcurrentHashMap.newKeySet<DegradationRequest>()

    override fun requireConfigurationCacheDegradation(reason: String, spec: Provider<Boolean>) {
        val trace = userCodeApplicationContext.current()?.let { PropertyTrace.BuildLogic(it.source) }
            ?: PropertyTrace.Unknown
        buildLogicDegradationRequests.add(DegradationRequest(trace, reason, spec))
    }

    override fun requireConfigurationCacheDegradation(task: Task, reason: String, spec: Provider<Boolean>) {
        val trace = PropertyTrace.Task(GeneratedSubclasses.unpackType(task), (task as TaskInternal).identityPath.path)
        tasksDegradationRequests.add(DegradationRequest(trace, reason, spec))
    }

    fun getDegradationReasonsForTask(trace: PropertyTrace): List<String> =
        tasksDegradationRequests
            .filter { it.trace == trace && it.spec.getOrElse(false) }
            .map { it.reason }

    fun getBuildLogicDegradationReasons(): Map<PropertyTrace, List<String>> =
        buildLogicDegradationRequests
            .filter { it.spec.getOrElse(false) }
            .groupBy({ it.trace }, { it.reason })

    private data class DegradationRequest(
        val trace: PropertyTrace,
        val reason: String,
        val spec: Provider<Boolean>
    )
}
