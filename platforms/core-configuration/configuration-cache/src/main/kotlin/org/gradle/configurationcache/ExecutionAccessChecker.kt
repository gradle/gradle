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

import org.gradle.api.internal.provider.ConfigurationTimeBarrier
import org.gradle.execution.ExecutionAccessChecker
import org.gradle.execution.ExecutionAccessListener


internal
class ConfigurationTimeBarrierBasedExecutionAccessChecker(
    private val configurationTimeBarrier: ConfigurationTimeBarrier,
    private val broadcaster: ExecutionAccessListener
) : ExecutionAccessChecker {
    override fun disallowedAtExecutionInjectedServiceAccessed(injectedServiceType: Class<*>, getterName: String, consumer: String) {
        if (shouldReportExecutionTimeAccess()) {
            broadcaster.disallowedAtExecutionInjectedServiceAccessed(injectedServiceType, getterName, consumer)
        }
    }

    private
    fun shouldReportExecutionTimeAccess(): Boolean =
        !configurationTimeBarrier.isAtConfigurationTime
}


internal
class DefaultExecutionAccessChecker : ExecutionAccessChecker {

    override fun disallowedAtExecutionInjectedServiceAccessed(injectedServiceType: Class<*>, getterName: String, consumer: String) {}
}
