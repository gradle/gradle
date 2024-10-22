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

package org.gradle.internal.cc.impl

import org.gradle.cache.internal.streams.BlockAddress
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.buildtree.BuildTreeWorkGraph
import org.gradle.internal.cc.impl.cacheentry.EntryDetails
import org.gradle.internal.cc.impl.cacheentry.ModelKey
import org.gradle.internal.cc.impl.serialize.Codecs
import org.gradle.internal.serialize.graph.CloseableReadContext
import org.gradle.internal.serialize.graph.CloseableWriteContext
import org.gradle.internal.serialize.graph.MutableReadContext
import org.gradle.internal.serialize.graph.SpecialDecoders
import org.gradle.internal.serialize.graph.SpecialEncoders
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.util.Path
import java.io.InputStream
import java.io.OutputStream

internal
interface ConfigurationCacheBuildTreeIO : ConfigurationCacheOperationIO {

    fun writeCacheEntryDetailsTo(
        buildStateRegistry: BuildStateRegistry,
        intermediateModels: Map<ModelKey, BlockAddress>,
        projectMetadata: Map<Path, BlockAddress>,
        sideEffects: List<BlockAddress>,
        stateFile: ConfigurationCacheStateFile
    )

    fun readCacheEntryDetailsFrom(stateFile: ConfigurationCacheStateFile): EntryDetails?

    /**
     * See [ConfigurationCacheState.writeRootBuildState].
     */
    fun writeRootBuildStateTo(stateFile: ConfigurationCacheStateFile)

    fun readRootBuildStateFrom(
        stateFile: ConfigurationCacheStateFile,
        loadAfterStore: Boolean,
        graph: BuildTreeWorkGraph,
        graphBuilder: BuildTreeWorkGraphBuilder?
    ): Pair<String, BuildTreeWorkGraph.FinalizedGraph>

    fun writeModelTo(model: Any, stateFile: ConfigurationCacheStateFile)

    fun readModelFrom(stateFile: ConfigurationCacheStateFile): Any

    /**
     * @param profile the unique name associated with the output stream for debugging space usage issues
     */
    fun writeContextFor(
        name: String,
        stateType: StateType,
        outputStream: () -> OutputStream,
        profile: () -> String,
        specialEncoders: SpecialEncoders = SpecialEncoders(),
    ): Pair<CloseableWriteContext, Codecs>

    fun <R> withReadContextFor(
        stateFile: ConfigurationCacheStateFile,
        specialDecoders: SpecialDecoders = SpecialDecoders(),
        readOperation: suspend MutableReadContext.(Codecs) -> R
    ): R =
        withReadContextFor(stateFile.stateFile.name, stateFile.stateType, stateFile::inputStream, specialDecoders, readOperation)

    fun <R> withReadContextFor(
        name: String,
        stateType: StateType,
        inputStream: () -> InputStream,
        specialDecoders: SpecialDecoders = SpecialDecoders(),
        readOperation: suspend MutableReadContext.(Codecs) -> R
    ): R

    fun <R> withReadContextFor(
        readContext: CloseableReadContext,
        codecs: Codecs,
        readOperation: suspend MutableReadContext.(Codecs) -> R
    ): R

    fun <R> withWriteContextFor(
        stateFile: ConfigurationCacheStateFile,
        profile: () -> String,
        specialEncoders: SpecialEncoders,
        writeOperation: suspend WriteContext.(Codecs) -> R
    ): R =
        withWriteContextFor(stateFile.stateFile.name, stateFile.stateType, stateFile::outputStream, profile, specialEncoders, writeOperation)

    fun <R> withWriteContextFor(
        name: String,
        stateType: StateType,
        outputStream: () -> OutputStream,
        profile: () -> String,
        specialEncoders: SpecialEncoders,
        writeOperation: suspend WriteContext.(Codecs) -> R
    ): R
}
