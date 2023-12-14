/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.configurationcache.models

import com.google.common.collect.Sets.newConcurrentHashSet
import org.gradle.cache.internal.streams.BlockAddress
import org.gradle.cache.internal.streams.ValueStore
import org.gradle.configurationcache.CheckedFingerprint
import org.gradle.configurationcache.ConfigurationCacheStateStore
import org.gradle.configurationcache.StateType
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.util.Path
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer


/**
 * Responsible for loading and storing intermediate models used during tooling API build action execution.
 */
internal
abstract class ProjectStateStore<K, V>(
    private val store: ConfigurationCacheStateStore,
    private val stateType: StateType
) : Closeable {
    private
    val valuesStore by lazy {
        val writer = ValueStore.Writer<V> { encoder, value ->
            write(encoder, value)
        }
        val reader = ValueStore.Reader { decoder ->
            read(decoder)
        }
        store.createValueStore(stateType, writer, reader)
    }

    private
    val previousValues = ConcurrentHashMap<K, BlockAddress>()

    private
    val currentValues = ConcurrentHashMap<K, BlockAddress>()

    private
    val crossBuildUnsafe = newConcurrentHashSet<K>()

    protected
    abstract fun projectPathForKey(key: K): Path?

    protected
    abstract fun write(encoder: Encoder, value: V)

    protected
    abstract fun read(decoder: Decoder): V

    /**
     * All values used during execution.
     */
    val values: Map<K, BlockAddress>
        get() = currentValues.filterKeys { it !in crossBuildUnsafe }

    fun restoreFromCacheEntry(entryDetails: Map<K, BlockAddress>, checkedFingerprint: CheckedFingerprint.ProjectsInvalid) {
        for (entry in entryDetails) {
            val identityPath = projectPathForKey(entry.key)
            if (identityPath == null || !checkedFingerprint.invalidProjects.contains(identityPath)) {
                // Can reuse the value
                previousValues[entry.key] = entry.value
            }
        }
    }

    fun visitProjects(reusedProjects: Consumer<Path>, updatedProjects: Consumer<Path>) {
        val previousProjects = previousValues.keys.mapNotNullTo(hashSetOf()) { projectPathForKey(it) }
        val currentProjects = currentValues.keys.mapNotNull { projectPathForKey(it) }
        for (path in currentProjects) {
            if (previousProjects.contains(path)) {
                reusedProjects.accept(path)
            } else {
                updatedProjects.accept(path)
            }
        }
    }

    fun loadOrCreateValue(key: K, creator: () -> Value<V>): Value<V> {
        val addressOfCached = locateCachedValue(key)
        if (addressOfCached != null) {
            try {
                val loaded = valuesStore.read(addressOfCached)
                return Value(loaded, key !in crossBuildUnsafe)
            } catch (e: Exception) {
                throw RuntimeException("Could not load entry for $key", e)
            }
        }
        // TODO - should protect from concurrent creation
        val created = creator()
        if (!created.crossBuildReusable) {
            crossBuildUnsafe += key
        }
        val address = valuesStore.write(created.value)
        currentValues[key] = address
        return created
    }

    private
    fun locateCachedValue(key: K): BlockAddress? {
        val cachedInCurrent = currentValues[key]
        if (cachedInCurrent != null) {
            return cachedInCurrent
        }
        val cachedInPrevious = previousValues[key]
        if (cachedInPrevious != null) {
            currentValues[key] = cachedInPrevious
        }
        return cachedInPrevious
    }

    override fun close() {
        CompositeStoppable.stoppable(valuesStore).stop()
    }

    data class Value<T>(
        val value: T,
        val crossBuildReusable: Boolean
    )
}
