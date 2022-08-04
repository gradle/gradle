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
 *
 * The class can operate in two modes. First is the tracking mode when all environment-modifying operations are stored
 * and the list of the operations can be retrieved as the CachedEnvironmentState object. This mode is intended for the
 * builds with the configuration phase. The second mode applies the restored state to the environment and doesn't
 * track anything. This mode is used when the configuration is restored from the configuration cache.
 * Mode selection happens upon the first use of the class. Calling an operation that isn't supported in the current
 * mode results in the IllegalStateException.
 */
@ServiceScope(Scopes.BuildTree::class)
class EnvironmentChangeTracker(private val userCodeApplicationContext: UserCodeApplicationContext) {
    private
    val mode = ModeHolder()

    fun isSystemPropertyMutated(key: String) = mode.toTrackingMode().isSystemPropertyMutated(key)

    fun loadFrom(storedState: CachedEnvironmentState) = mode.toRestoringMode().loadFrom(storedState)

    fun getCachedState() = mode.toTrackingMode().getCachedState()

    fun systemPropertyChanged(key: Any, value: Any?, consumer: String) = mode.toTrackingMode().systemPropertyChanged(key, value, consumer)

    fun systemPropertyRemoved(key: Any) = mode.toTrackingMode().systemPropertyRemoved(key)

    fun systemPropertiesCleared() = mode.toTrackingMode().systemPropertiesCleared()

    private
    inner class ModeHolder {
        // ModeHolder encapsulates concurrent mode updates.
        private
        var mode: TrackerMode = Initial()

        private
        inline fun <T : TrackerMode> setMode(transition: (TrackerMode) -> T): T {
            synchronized(this) {
                val newMode = transition(mode)
                mode = newMode
                return newMode
            }
        }

        fun toTrackingMode(): Tracking = setMode(TrackerMode::toTracking)

        fun toRestoringMode(): Restoring = setMode(TrackerMode::toRestoring)
    }

    private
    sealed interface TrackerMode {
        fun toTracking(): Tracking
        fun toRestoring(): Restoring
    }

    private
    inner class Initial : TrackerMode {
        override fun toTracking(): Tracking {
            return Tracking()
        }

        override fun toRestoring(): Restoring {
            return Restoring()
        }
    }

    private
    inner class Tracking : TrackerMode {
        private
        val mutatedSystemProperties = ConcurrentHashMap<Any, SystemPropertyChange>()

        @Volatile
        private
        var systemPropertiesCleared = false

        override fun toTracking(): Tracking {
            return this
        }

        override fun toRestoring(): Restoring {
            throw IllegalStateException("Cannot restore state because change tracking is already in progress")
        }

        fun isSystemPropertyMutated(key: String): Boolean {
            return systemPropertiesCleared || mutatedSystemProperties.containsKey(key)
        }

        fun getCachedState(): CachedEnvironmentState {
            return CachedEnvironmentState(
                cleared = systemPropertiesCleared,
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

        fun systemPropertiesCleared() {
            systemPropertiesCleared = true
            mutatedSystemProperties.clear()
        }
    }

    private
    class Restoring : TrackerMode {
        override fun toTracking(): Tracking {
            throw IllegalStateException("Cannot track state because it was restored")
        }

        override fun toRestoring(): Restoring {
            throw IllegalStateException("Cannot restore state because it was already restored")
        }

        fun loadFrom(storedState: CachedEnvironmentState) {
            if (storedState.cleared) {
                System.getProperties().clear()
            }

            storedState.updates.forEach { update ->
                System.getProperties()[update.key] = update.value
            }

            storedState.removals.forEach { removal ->
                System.clearProperty(removal.key)
            }
        }
    }

    class CachedEnvironmentState(val cleared: Boolean, val updates: List<SystemPropertySet>, val removals: List<SystemPropertyRemove>)

    sealed class SystemPropertyChange

    class SystemPropertySet(val key: Any, val value: Any?, val location: PropertyTrace?) : SystemPropertyChange()
    class SystemPropertyRemove(val key: String) : SystemPropertyChange()

    /**
     * This is a placeholder for system properties that were set but then removed. Having this in the map marks
     * the property as mutated for the rest of the configuration phase but doesn't store the key in cache.
     */
    object SystemPropertyIgnored : SystemPropertyChange()
}
