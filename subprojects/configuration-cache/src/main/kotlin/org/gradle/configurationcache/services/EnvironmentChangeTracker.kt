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

        storedState.removals.forEach { removal ->
            mutatedSystemProperties[removal.key] = removal
            System.clearProperty(removal.key)
        }
    }

    fun getCachedState(): CachedEnvironmentState {
        return CachedEnvironmentState(
            updates = mutatedSystemProperties.values.filterIsInstance<SystemPropertySet>(),
            removals = mutatedSystemProperties.values.filterIsInstance<SystemPropertyRemove>()
        )
    }

    fun systemPropertyChanged(key: Any, value: Any?, consumer: String) {
        mutatedSystemProperties[key] = SystemPropertySet(key, value, userCodeApplicationContext.location(consumer))
    }

    fun systemPropertyRemoved(key: Any) {
        if (key is String) {
            // Externally set system properties can only use Strings as keys. If the removal argument is a string
            // then it can affect externally set property and has to be persisted. Then removal will be applied on
            // the next run from cache.
            mutatedSystemProperties[key] = SystemPropertyRemove(key)
        } else {
            // If the key is not a string, it can only affect properties that were set by the build logic. There is
            // no need to persist the removal, but the set value should not be persisted too. A placeholder value
            // will keep the key mutated to avoid recording it as an input.
            mutatedSystemProperties[key] = SystemPropertyIgnored
        }
    }

    class CachedEnvironmentState(val updates: List<SystemPropertySet>, val removals: List<SystemPropertyRemove>)

    sealed class SystemPropertyChange

    class SystemPropertySet(val key: Any, val value: Any?, val location: PropertyTrace?) : SystemPropertyChange()
    class SystemPropertyRemove(val key: String) : SystemPropertyChange()

    /**
     * This is a placeholder for system properties that were set but then removed. Having this in the map marks
     * the property as mutated for the rest of the configuration phase but doesn't store the key in cache.
     */
    object SystemPropertyIgnored : SystemPropertyChange()
}
