/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.Project
import org.gradle.api.internal.provider.ConfigurationTimeBarrier
import org.gradle.api.invocation.Gradle
import org.gradle.execution.ExecutionAccessChecker
import org.gradle.execution.ExecutionAccessListener
import org.gradle.internal.buildtree.BuildModelParameters

private
val disallowedAtExecutionTimeServices = setOf(
    Project::class.java,
    Gradle::class.java
)

internal
class DefaultExecutionAccessChecker(
    private val configurationTimeBarrier: ConfigurationTimeBarrier,
    private val modelParameters: BuildModelParameters,
    private val broadcaster: ExecutionAccessListener
) : ExecutionAccessChecker {

    override fun notifyInjectedServiceAccess(injectedServiceType: Class<*>, consumer: String) {
        if (shouldReportExecutionTimeAccess() &&
            isDisallowedAtExecutionTimeService(injectedServiceType)) {
            broadcaster.onInjectedServiceAccess(injectedServiceType, consumer)
        }
    }

    private fun isDisallowedAtExecutionTimeService(injectedServiceType: Class<*>): Boolean =
        disallowedAtExecutionTimeServices.any { it.isAssignableFrom(injectedServiceType) }

    private fun shouldReportExecutionTimeAccess(): Boolean =
        modelParameters.isConfigurationCache && !configurationTimeBarrier.isAtConfigurationTime

}
