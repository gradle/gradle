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
import org.gradle.internal.build.BuildState
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.buildtree.BuildTreeWorkGraph
import org.gradle.internal.cc.impl.cacheentry.EntryDetails
import org.gradle.internal.cc.impl.cacheentry.ModelKey
import org.gradle.internal.cc.impl.serialize.ConfigurationCacheCodecs
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.PositionAwareEncoder
import org.gradle.internal.serialize.graph.ClassDecoder
import org.gradle.internal.serialize.graph.ClassEncoder
import org.gradle.internal.serialize.graph.CloseableReadContext
import org.gradle.internal.serialize.graph.CloseableWriteContext
import org.gradle.internal.serialize.graph.MutableReadContext
import org.gradle.internal.serialize.graph.SpecialDecoders
import org.gradle.internal.serialize.graph.SpecialEncoders
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.util.Path
import java.io.InputStream
import java.io.OutputStream

@ServiceScope(Scope.Build::class)
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
    fun writeRootBuildStateTo(rootBuild: BuildState, stateFile: ConfigurationCacheStateFile)

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
        customClassEncoder: ClassEncoder? = null
    ): Pair<CloseableWriteContext, ConfigurationCacheCodecs>

    fun encoderFor(
        stateType: StateType,
        outputStream: () -> OutputStream
    ): PositionAwareEncoder

    fun decoderFor(
        stateType: StateType,
        inputStream: () -> InputStream
    ): Decoder

    fun <R> withReadContextFor(
        stateFile: ConfigurationCacheStateFile,
        specialDecoders: SpecialDecoders = SpecialDecoders(),
        customClassDecoder: ClassDecoder? = null,
        readOperation: suspend MutableReadContext.(ConfigurationCacheCodecs) -> R
    ): R =
        withReadContextFor(
            stateFile.stateFile.name,
            stateFile.stateType,
            stateFile::inputStream,
            specialDecoders,
            customClassDecoder,
            readOperation
        )

    fun <R> withReadContextFor(
        name: String,
        stateType: StateType,
        inputStream: () -> InputStream,
        specialDecoders: SpecialDecoders = SpecialDecoders(),
        customClassDecoder: ClassDecoder? = null,
        readOperation: suspend MutableReadContext.(ConfigurationCacheCodecs) -> R
    ): R

    fun <R> withReadContextFor(
        readContext: CloseableReadContext,
        codecs: ConfigurationCacheCodecs,
        readOperation: suspend MutableReadContext.(ConfigurationCacheCodecs) -> R
    ): R

    fun <R> withWriteContextFor(
        stateFile: ConfigurationCacheStateFile,
        profile: () -> String,
        specialEncoders: SpecialEncoders = SpecialEncoders(),
        customClassEncoder: ClassEncoder? = null,
        writeOperation: suspend WriteContext.(ConfigurationCacheCodecs) -> R
    ): R =
        withWriteContextFor(
            stateFile.stateFile.name,
            stateFile.stateType,
            stateFile::outputStream,
            profile,
            specialEncoders,
            customClassEncoder,
            writeOperation
        )

    fun <R> withWriteContextFor(
        name: String,
        stateType: StateType,
        outputStream: () -> OutputStream,
        profile: () -> String,
        specialEncoders: SpecialEncoders,
        customClassEncoder: ClassEncoder?,
        writeOperation: suspend WriteContext.(ConfigurationCacheCodecs) -> R
    ): R

    fun readCandidateEntries(stateFile: ConfigurationCacheStateFile): List<CandidateEntry>
    fun writeCandidateEntries(stateFile: ConfigurationCacheStateFile, entries: List<CandidateEntry>)
}

data class CandidateEntry(
    val id: String
)
