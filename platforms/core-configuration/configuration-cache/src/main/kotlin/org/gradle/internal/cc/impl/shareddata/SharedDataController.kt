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

package org.gradle.internal.cc.impl.shareddata

import org.gradle.api.provider.Provider
import org.gradle.internal.cc.impl.ConfigurationCacheOperationIO
import org.gradle.internal.cc.impl.ConfigurationCacheStateStore
import org.gradle.internal.cc.impl.StateType
import org.gradle.internal.cc.impl.models.ProjectStateStore
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.graph.IsolateOwner
import org.gradle.internal.serialize.graph.readCollection
import org.gradle.internal.serialize.graph.withIsolate
import org.gradle.internal.serialize.graph.writeCollection
import org.gradle.internal.shareddata.SharedDataStorage.DataKey
import org.gradle.internal.shareddata.SharedDataStorage.ProjectProducedSharedData
import org.gradle.util.Path


internal
class SharedDataController(
    private val isolateOwner: IsolateOwner,
    private val cacheIO: ConfigurationCacheOperationIO,
//    fingerprintController: ConfigurationCacheFingerprintController,
    calculatedValueContainerFactory: CalculatedValueContainerFactory,
    store: ConfigurationCacheStateStore
) : ProjectStateStore<Path, ProjectProducedSharedData>(
    store,
    StateType.SharedData,
    "shared data",
    calculatedValueContainerFactory
    // TODO: fix this
    // It is important to write the shared data in the context of the producer project's fingerprint, so that, if the fingerprint gets invalidated, it is the producer project that gets reconfigured
//    writeProcedure = { path, doWrite -> fingerprintController.runCollectingFingerprintForProject(path, doWrite) }
) {
    override fun projectPathForKey(key: Path): Path = key

    override fun write(encoder: Encoder, value: ProjectProducedSharedData) {
        cacheIO.runWriteOperation(encoder) { codecs ->
            withIsolate(isolateOwner, codecs.userTypesCodec()) {
                writeCollection(value.allData.entries) { (key, value) ->
                    write(key)
                    write(value)
                }
            }
        }
    }

    override fun read(decoder: Decoder): ProjectProducedSharedData {
        return cacheIO.runReadOperation(decoder) { codecs ->
            withIsolate(isolateOwner, codecs.userTypesCodec()) {
                val map = buildMap {
                    readCollection {
                        put(read() as DataKey, read() as Provider<*>)
                    }
                }
                ProjectProducedSharedDataFromCache(map)
            }
        }
    }

    private
    class ProjectProducedSharedDataFromCache(val elementsMap: Map<DataKey, Provider<*>>) : ProjectProducedSharedData {
        override fun get(dataKey: DataKey): Provider<*>? = elementsMap[dataKey]
        override fun getAllData(): Map<DataKey, Provider<*>> = elementsMap
    }
}
