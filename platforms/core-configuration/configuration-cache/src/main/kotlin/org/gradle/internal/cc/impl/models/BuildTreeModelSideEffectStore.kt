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

package org.gradle.internal.cc.impl.models

import org.gradle.cache.internal.streams.BlockAddress
import org.gradle.cache.internal.streams.ValueStore
import org.gradle.internal.buildtree.BuildTreeModelSideEffect
import org.gradle.internal.cc.impl.ConfigurationCacheOperationIO
import org.gradle.internal.cc.impl.ConfigurationCacheStateStore
import org.gradle.internal.cc.impl.StateType
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.graph.IsolateOwner
import org.gradle.internal.serialize.graph.readNonNull
import org.gradle.internal.serialize.graph.withIsolate
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList


/**
 * Persists side effects observed during build action execution
 * and restores them on a subsequent load from the cache.
 *
 * @see BuildTreeModelSideEffect
 */
internal
class BuildTreeModelSideEffectStore(
    private val isolateOwner: IsolateOwner,
    private val cacheIO: ConfigurationCacheOperationIO,
    private val store: ConfigurationCacheStateStore,
) : Closeable {

    private
    val entries = CopyOnWriteArrayList<BlockAddress>()

    private
    val valuesStore by lazy {
        val writer = ValueStore.Writer<BuildTreeModelSideEffect> { encoder, value ->
            write(encoder, value)
        }
        val reader = ValueStore.Reader { decoder ->
            read(decoder)
        }
        store.createValueStore(StateType.ModelSideEffects, writer, reader)
    }

    fun collectSideEffects(): List<BlockAddress> = entries.toList()

    fun write(sideEffect: BuildTreeModelSideEffect) {
        val blockAddress = valuesStore.write(sideEffect)
        entries += blockAddress
    }

    fun restoreFromCacheEntry(sideEffects: List<BlockAddress>): List<BuildTreeModelSideEffect> {
        return sideEffects.map {
            valuesStore.read(it)
        }
    }

    private
    fun write(encoder: Encoder, value: BuildTreeModelSideEffect) {
        cacheIO.runWriteOperation(encoder) { codecs ->
            withIsolate(isolateOwner, codecs.userTypesCodec()) {
                write(value)
            }
        }
    }

    private
    fun read(decoder: Decoder): BuildTreeModelSideEffect {
        return cacheIO.runReadOperation(decoder) { codecs ->
            withIsolate(isolateOwner, codecs.userTypesCodec()) {
                readNonNull()
            }
        }
    }

    override fun close() {
        CompositeStoppable.stoppable(valuesStore).stop()
    }
}
