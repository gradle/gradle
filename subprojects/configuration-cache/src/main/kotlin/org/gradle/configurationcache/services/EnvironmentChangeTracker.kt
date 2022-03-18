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

package org.gradle.configurationcache.services

import org.gradle.configuration.internal.UserCodeApplicationContext
import org.gradle.configurationcache.problems.PropertyTrace
import org.gradle.configurationcache.problems.location
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope
import java.util.concurrent.ConcurrentHashMap


/**
 * The environment state that was mutated at the configuration phase and has to be restored before running a build from the cache.
 */
@ServiceScope(Scopes.BuildTree::class)
class EnvironmentChangeTracker(private val userCodeApplicationContext: UserCodeApplicationContext) {
    private
    val mutatedSystemProperties = ConcurrentHashMap<Any, SystemPropertyChange>()

    fun isSystemPropertyMutated(key: String): Boolean = mutatedSystemProperties.containsKey(key)

    fun loadFrom(storedState: CachedEnvironmentState) {
        storedState.updates.forEach { update ->
            mutatedSystemProperties[update.key] = update
            System.getProperties()[update.key] = update.value
        }
    }

    fun getCachedState(): CachedEnvironmentState {
        return CachedEnvironmentState(mutatedSystemProperties.values.filterIsInstance<SystemPropertySet>())
    }

    fun systemPropertyChanged(key: Any, value: Any?, consumer: String) {
        mutatedSystemProperties[key] = SystemPropertySet(key, value, userCodeApplicationContext.location(consumer))
    }

    class CachedEnvironmentState(val updates: List<SystemPropertySet>)

    sealed class SystemPropertyChange(val key: Any)

    class SystemPropertySet(key: Any, val value: Any?, val location: PropertyTrace?) : SystemPropertyChange(key)
}
