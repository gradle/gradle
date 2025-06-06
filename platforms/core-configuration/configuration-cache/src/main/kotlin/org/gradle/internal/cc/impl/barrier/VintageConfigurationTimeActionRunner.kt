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

package org.gradle.internal.cc.impl.barrier

import org.gradle.api.configuration.BuildFeatures
import org.gradle.api.internal.provider.ConfigurationTimeBarrier
import org.gradle.api.internal.provider.DefaultConfigurationTimeBarrier
import org.gradle.internal.cc.impl.ConfigurationCacheInputsListener
import org.gradle.internal.configuration.inputs.InstrumentedInputs
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope

/**
 * Runs parts of the vintage build with the expected state of the [ConfigurationTimeBarrier] and other auxiliary CC-related machinery.
 *
 * This class is not intended to be used with CC enabled.
 */
@ServiceScope(Scope.BuildTree::class)
internal class VintageConfigurationTimeActionRunner(
    buildFeatures: BuildFeatures,
    configurationTimeBarrier: ConfigurationTimeBarrier,
    private val inputsListener: ConfigurationCacheInputsListener
) {
    private val configurationTimeBarrier = configurationTimeBarrier as DefaultConfigurationTimeBarrier
    init {
        require(!buildFeatures.configurationCache.active.get()) {
            "This class should not be used with the configuration cache enabled."
        }
    }
    fun <T> runConfigurationTimeAction(action: () -> T): T {
        configurationTimeBarrier.prepare()
        InstrumentedInputs.setListener(inputsListener)
        try {
            return action()
        } finally {
            configurationTimeBarrier.cross()
            InstrumentedInputs.discardListener()
        }
    }
}
