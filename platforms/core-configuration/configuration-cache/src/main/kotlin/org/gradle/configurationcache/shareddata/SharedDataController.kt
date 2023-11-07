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

package org.gradle.configurationcache.shareddata

import org.gradle.api.provider.Provider
import org.gradle.configurationcache.ConfigurationCacheIO
import org.gradle.configurationcache.ConfigurationCacheStateStore
import org.gradle.configurationcache.DefaultConfigurationCache
import org.gradle.configurationcache.StateType
import org.gradle.configurationcache.fingerprint.ConfigurationCacheFingerprintController
import org.gradle.configurationcache.models.ProjectStateStore
import org.gradle.configurationcache.serialization.IsolateOwner
import org.gradle.configurationcache.serialization.readCollection
import org.gradle.configurationcache.serialization.runReadOperation
import org.gradle.configurationcache.serialization.runWriteOperation
import org.gradle.configurationcache.serialization.writeCollection
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.shareddata.SharedDataStorage.DataKey
import org.gradle.internal.shareddata.SharedDataStorage.ProjectProducedSharedData
import org.gradle.util.Path

internal class SharedDataController(
    private val host: DefaultConfigurationCache.Host,
    private val cacheIO: ConfigurationCacheIO,
    fingerprintController: ConfigurationCacheFingerprintController,
    store: ConfigurationCacheStateStore
) : ProjectStateStore<Path, ProjectProducedSharedData>(
    store,
    StateType.SharedData,
    // It is important to write the shared data in the context of the producer project's fingerprint, so that, if the fingerprint gets invalidated, it is the producer project that gets reconfigured
    writeProcedure = { path, doWrite -> fingerprintController.collectFingerprintForProject(path, doWrite) }
) {
    override fun projectPathForKey(key: Path): Path = key

    override fun write(encoder: Encoder, value: ProjectProducedSharedData) {
        val (context, codecs) = cacheIO.writerContextFor(encoder)
        context.push(IsolateOwner.OwnerHost(host), codecs.userTypesCodec())
        context.runWriteOperation {
            writeCollection(value.allData.entries) { (key, value) ->
                write(key)
                write(value)
            }
        }
    }

    override fun read(decoder: Decoder): ProjectProducedSharedData {
        val (context, codecs) = cacheIO.readerContextFor(decoder)
        context.push(IsolateOwner.OwnerHost(host), codecs.userTypesCodec())
        return context.runReadOperation {
            val map = buildMap {
                readCollection {
                    put(read() as DataKey, read() as Provider<*>)
                }
            }
            ProjectProducedSharedDataFromCache(map)
        }
    }

    private
    class ProjectProducedSharedDataFromCache(val elementsMap: Map<DataKey, Provider<*>>) : ProjectProducedSharedData {
        override fun get(dataKey: DataKey): Provider<*>? = elementsMap[dataKey]
        override fun getAllData(): Map<DataKey, Provider<*>> = elementsMap
    }
}
