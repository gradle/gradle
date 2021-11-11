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

import org.gradle.cache.internal.streams.BlockAddress
import org.gradle.cache.internal.streams.ValueStore
import org.gradle.configurationcache.CheckedFingerprint
import org.gradle.configurationcache.ConfigurationCacheIO
import org.gradle.configurationcache.ConfigurationCacheStateStore
import org.gradle.configurationcache.DefaultConfigurationCache
import org.gradle.configurationcache.StateType
import org.gradle.configurationcache.cacheentry.EntryDetails
import org.gradle.configurationcache.cacheentry.ModelKey
import org.gradle.configurationcache.extensions.uncheckedCast
import org.gradle.configurationcache.fingerprint.ConfigurationCacheFingerprintController
import org.gradle.configurationcache.serialization.IsolateOwner
import org.gradle.configurationcache.serialization.runReadOperation
import org.gradle.configurationcache.serialization.runWriteOperation
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.util.Path
import java.io.Closeable
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap


/**
 * Responsible for loading and storing intermediate models used during tooling API build action execution.
 */
internal
class IntermediateModelController(
    private val host: DefaultConfigurationCache.Host,
    private val cacheIO: ConfigurationCacheIO,
    private val store: ConfigurationCacheStateStore,
    private val cacheFingerprintController: ConfigurationCacheFingerprintController
) : Closeable {
    private
    val modelsStore by lazy {
        val writer = ValueStore.Writer<Any?> { encoder, value ->
            val (context, codecs) = cacheIO.writerContextFor(encoder)
            context.push(IsolateOwner.OwnerHost(host), codecs.userTypesCodec)
            context.runWriteOperation {
                write(value)
            }
        }
        val reader = ValueStore.Reader<Any?> { decoder ->
            val (context, codecs) = cacheIO.readerContextFor(decoder)
            context.push(IsolateOwner.OwnerHost(host), codecs.userTypesCodec)
            context.runReadOperation {
                read()
            }
        }
        store.createValueStore(StateType.IntermediateModels, writer, reader)
    }

    private
    val previousIntermediateModels = ConcurrentHashMap<ModelKey, BlockAddress>()

    private
    val intermediateModels = ConcurrentHashMap<ModelKey, BlockAddress>()

    /**
     * All models used during execution.
     */
    val models: Map<ModelKey, BlockAddress>
        get() = Collections.unmodifiableMap(intermediateModels)

    fun restoreFromCacheEntry(entryDetails: EntryDetails, checkedFingerprint: CheckedFingerprint.ProjectsInvalid) {
        for (entry in entryDetails.intermediateModels) {
            if (entry.key.identityPath == null || !checkedFingerprint.invalidProjects.contains(entry.key.identityPath)) {
                // Can reuse the model
                previousIntermediateModels[entry.key] = entry.value
            }
        }
    }

    fun <T> loadOrCreateIntermediateModel(identityPath: Path?, modelName: String, creator: () -> T): T? {
        val key = ModelKey(identityPath, modelName)
        val addressOfCached = locateCachedModel(key)
        if (addressOfCached != null) {
            return modelsStore.read(addressOfCached)?.uncheckedCast()
        }
        val model = if (identityPath != null) {
            cacheFingerprintController.collectFingerprintForProject(identityPath, creator)
        } else {
            creator()
        }
        val address = modelsStore.write(model)
        intermediateModels[key] = address
        return model
    }

    private
    fun locateCachedModel(key: ModelKey): BlockAddress? {
        val cachedInCurrent = intermediateModels[key]
        if (cachedInCurrent != null) {
            return cachedInCurrent
        }
        val cachedInPrevious = previousIntermediateModels[key]
        if (cachedInPrevious != null) {
            intermediateModels[key] = cachedInPrevious
        }
        return cachedInPrevious
    }

    override fun close() {
        CompositeStoppable.stoppable(modelsStore).stop()
    }
}
