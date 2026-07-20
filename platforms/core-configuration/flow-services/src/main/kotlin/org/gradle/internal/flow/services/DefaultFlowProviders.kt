/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.flow.services

import org.gradle.api.configuration.ConfigurationCacheOutcome
import org.gradle.api.flow.BuildWorkResult
import org.gradle.api.flow.FlowProviders
import org.gradle.api.internal.provider.AbstractMinimalProvider
import org.gradle.api.internal.provider.ValueSupplier
import org.gradle.api.provider.Provider
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope


@ServiceScope(Scope.Build::class)
class DefaultFlowProviders(
    private val services: ServiceRegistry
) : FlowProviders {

    private
    val buildWorkResult by lazy {
        BuildWorkResultProvider()
    }

    private
    val configurationCacheOutcome by lazy {
        ConfigurationCacheOutcomeProvider(services)
    }

    override fun getBuildWorkResult(): Provider<BuildWorkResult> =
        buildWorkResult

    override fun getConfigurationCacheOutcome(): Provider<ConfigurationCacheOutcome> =
        configurationCacheOutcome
}


class BuildWorkResultProvider : AbstractMinimalProvider<BuildWorkResult>() {

    private
    var result: BuildWorkResult? = null

    fun set(result: BuildWorkResult) {
        require(this.result == null)
        this.result = result
    }

    override fun getType(): Class<BuildWorkResult> =
        BuildWorkResult::class.java

    override fun calculateOwnValue(consumer: ValueSupplier.ValueConsumer): ValueSupplier.Value<out BuildWorkResult> {
        require(result != null) {
            "Cannot access the value of '${BuildWorkResult::class.simpleName}' before it becomes available!"
        }
        return ValueSupplier.Value.ofNullable(result)
    }

    override fun calculateExecutionTimeValue(): ValueSupplier.ExecutionTimeValue<out BuildWorkResult> =
        ValueSupplier.ExecutionTimeValue.changingValue(this)
}


class ConfigurationCacheOutcomeProvider(
    private val services: ServiceRegistry
) : AbstractMinimalProvider<ConfigurationCacheOutcome>() {

    @Volatile
    private
    var available = false

    /**
     * Resolved lazily on first query, never at [markAvailable] time: eager resolution would freeze
     * the configuration cache problem summary early on every build, even ones that never use this
     * provider, changing the problem counts reported at the end of the build.
     */
    private
    val outcome by lazy {
        val source = services.find(ConfigurationCacheOutcomeSource::class.java) as ConfigurationCacheOutcomeSource?
        source?.outcome() ?: ConfigurationCacheOutcome.NOT_ENABLED
    }

    fun markAvailable() {
        available = true
    }

    override fun getType(): Class<ConfigurationCacheOutcome> =
        ConfigurationCacheOutcome::class.java

    override fun calculateOwnValue(consumer: ValueSupplier.ValueConsumer): ValueSupplier.Value<out ConfigurationCacheOutcome> {
        require(available) {
            "Cannot access the value of '${ConfigurationCacheOutcome::class.simpleName}' before it becomes available!"
        }
        return ValueSupplier.Value.of(outcome)
    }

    override fun calculateExecutionTimeValue(): ValueSupplier.ExecutionTimeValue<out ConfigurationCacheOutcome> =
        ValueSupplier.ExecutionTimeValue.changingValue(this)
}
