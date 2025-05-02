/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.cc.base.services

import org.gradle.initialization.EnvironmentChangeTracker
import org.gradle.internal.configuration.problems.ProblemFactory
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.service.scopes.Scope
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

@ServiceScope(Scope.BuildTree::class)
class ConfigurationCacheEnvironmentChangeTracker(private val problemFactory: ProblemFactory) : EnvironmentChangeTracker {
    private
    val mode = ModeHolder()

    override fun systemPropertyChanged(key: Any, value: Any?, consumer: String?) =
        mode.toTrackingMode().systemPropertyChanged(key, value, consumer)

    override fun systemPropertyLoaded(key: Any, value: Any?, oldValue: Any?) {
        mode.toTrackingMode().systemPropertyLoaded(key, value, oldValue)
    }

    override fun systemPropertyOverridden(key: Any) =
        mode.toTrackingMode().systemPropertyOverridden(key)

    fun isSystemPropertyMutated(key: String) = mode.toTrackingMode().isSystemPropertyMutated(key)

    fun isSystemPropertyLoaded(key: String) = mode.toTrackingMode().isSystemPropertyLoaded(key)

    fun getLoadedPropertyOldValue(key: String) = mode.toTrackingMode().getLoadedPropertyOldValue(key)

    fun loadFrom(storedState: CachedEnvironmentState) = mode.toRestoringMode().loadFrom(storedState)

    fun getCachedState() = mode.toTrackingMode().getCachedState()

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
            return Restoring(emptySet())
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
            // Restoration must consider properties that was overridden after store.
            // When property was loaded and stored then loaded value will be presented for execution time after restore.
            // This is a wrong behavior if property was overridden. Execution time must see overridden value instead of restored one.
            // Overridden properties keys are passed to be excluded from the restoration process.
            return Restoring(mutatedSystemProperties.filterValues { it is SystemPropertyOverride }.keys)
        }

        fun isSystemPropertyMutated(key: String): Boolean {
            return systemPropertiesCleared || mutatedSystemProperties[key] is SystemPropertyMutate
        }

        fun isSystemPropertyLoaded(key: String): Boolean {
            return mutatedSystemProperties[key] is SystemPropertyLoad
        }

        fun getLoadedPropertyOldValue(key: String): Any? {
            return (mutatedSystemProperties[key] as? SystemPropertyLoad)?.oldValue
        }

        fun getCachedState(): CachedEnvironmentState {
            return CachedEnvironmentState(
                cleared = systemPropertiesCleared,
                updates = mutatedSystemProperties.values.filterIsInstance<SystemPropertySet>(),
                removals = mutatedSystemProperties.values.filterIsInstance<SystemPropertyRemove>()
            )
        }

        fun systemPropertyOverridden(key: Any) {
            mutatedSystemProperties[key] = SystemPropertyOverride
        }

        fun systemPropertyLoaded(key: Any, value: Any?, oldValue: Any?) {
            val loadedOldValue = when (val loadedSystemProperty = mutatedSystemProperties[key]) {
                is SystemPropertyLoad -> loadedSystemProperty.oldValue
                else -> oldValue
            }

            mutatedSystemProperties[key] = SystemPropertyLoad(key, value, loadedOldValue)
        }

        fun systemPropertyChanged(key: Any, value: Any?, consumer: String?) {
            mutatedSystemProperties[key] = SystemPropertyMutate(key, value, problemFactory.locationForCaller(consumer))
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
    class Restoring(private val overriddenSystemProperties: Set<Any>) : TrackerMode {
        override fun toTracking(): Tracking {
            error("Cannot track state because it was restored")
        }

        override fun toRestoring(): Restoring {
            error("Cannot restore state because it was already restored")
        }

        fun loadFrom(storedState: CachedEnvironmentState) {
            if (storedState.cleared) {
                System.getProperties().clear()
            }

            storedState.updates
                .filter { update ->
                    // Only loaded properties can be overridden.
                    // Mutated properties are taking precedence over overridden because the
                    // first ones defined in build logic, hence we want to restore it.
                    update !is SystemPropertyLoad || !overriddenSystemProperties.contains(update.key)
                }
                .forEach { update ->
                    System.getProperties()[update.key] = update.value
                }

            storedState.removals.forEach { removal ->
                System.clearProperty(removal.key)
            }
        }
    }

    class CachedEnvironmentState(val cleared: Boolean, val updates: List<SystemPropertySet>, val removals: List<SystemPropertyRemove>)

    sealed class SystemPropertyChange

    sealed class SystemPropertySet(val key: Any, val value: Any?, val location: PropertyTrace) : SystemPropertyChange()

    class SystemPropertyMutate(key: Any, value: Any?, location: PropertyTrace) : SystemPropertySet(key, value, location)

    class SystemPropertyLoad(key: Any, value: Any?, val oldValue: Any?) : SystemPropertySet(key, value, PropertyTrace.Unknown)

    object SystemPropertyOverride : SystemPropertyChange()

    class SystemPropertyRemove(val key: String) : SystemPropertyChange()

    /**
     * This is a placeholder for system properties that were set but then removed. Having this in the map marks
     * the property as mutated for the rest of the configuration phase but doesn't store the key in cache.
     */
    object SystemPropertyIgnored : SystemPropertyChange()
}
