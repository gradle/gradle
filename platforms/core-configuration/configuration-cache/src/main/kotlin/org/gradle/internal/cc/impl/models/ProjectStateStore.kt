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

package org.gradle.internal.cc.impl.models

import org.gradle.cache.internal.streams.BlockAddress
import org.gradle.cache.internal.streams.ValueStore
import org.gradle.internal.Describables
import org.gradle.internal.DisplayName
import org.gradle.internal.cc.impl.CheckedFingerprint
import org.gradle.internal.cc.impl.ConfigurationCacheStateStore
import org.gradle.internal.cc.impl.StateType
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.model.CalculatedValueContainer
import org.gradle.internal.model.CalculatedValueContainerFactory
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
    private val stateType: StateType,
    private val valueDescription: String,
    private val calculatedValueContainerFactory: CalculatedValueContainerFactory
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
    val currentValues = ConcurrentHashMap<K, CalculatedValueContainer<BlockAddress, *>>()

    protected
    abstract fun projectPathForKey(key: K): Path?

    protected
    abstract fun write(encoder: Encoder, value: V)

    protected
    abstract fun read(decoder: Decoder): V

    /**
     * Collects all values used during execution
     */
    fun collectAccessedValues(): Map<K, BlockAddress> =
        currentValues.mapValues { it.value.get() }

    fun restoreFromCacheEntry(entryDetails: Map<K, BlockAddress>, checkedFingerprint: CheckedFingerprint.ProjectsInvalid) {
        for (entry in entryDetails) {
            val identityPath = projectPathForKey(entry.key)
            if (identityPath == null || identityPath !in checkedFingerprint.invalidProjects) {
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

    /**
     * Create or load value, with load-after-store semantics
     */
    fun loadOrCreateValue(key: K, creator: () -> V): V {
        val address = loadOrCreateAddress(key, creator)
        return readValue(key, address)
    }

    private
    fun loadOrCreateAddress(key: K, creator: () -> V): BlockAddress {
        val valueContainer = currentValues.computeIfAbsent(key) { k ->
            createContainer(k, creator)
        }

        // Calculate the value after adding the entry to the map, so that the value container can take care of thread synchronization
        valueContainer.finalizeIfNotAlready()
        return valueContainer.get()
    }

    private
    fun createContainer(k: K, creator: () -> V): CalculatedValueContainer<BlockAddress, *> =
        calculatedValueContainerFactory.create<BlockAddress>(displayNameFor(k)) {
            loadPreviousOrCreateValue(k, creator)
        }

    private
    fun loadPreviousOrCreateValue(key: K, creator: () -> V): BlockAddress {
        previousValues[key]?.let { previouslyCached ->
            return previouslyCached
        }

        val value = creator()
        val address = valuesStore.write(value)
        // Only return the address to enforce load-after-store behavior
        return address
    }

    private
    fun displayNameFor(key: K): DisplayName {
        val unitDescription = projectPathForKey(key)?.let { "for project $it" } ?: "for build"
        return Describables.of(valueDescription, unitDescription)
    }

    private
    fun readValue(key: K, addressOfCached: BlockAddress): V {
        try {
            return valuesStore.read(addressOfCached)
        } catch (e: Exception) {
            throw RuntimeException("Could not load entry for $key", e)
        }
    }

    override fun close() {
        CompositeStoppable.stoppable(valuesStore).stop()
    }
}
