/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.logging.LogLevel
import org.gradle.cache.internal.streams.BlockAddress
import org.gradle.cache.internal.streams.BlockAddressSerializer
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.buildtree.BuildTreeWorkGraph
import org.gradle.internal.cc.base.logger
import org.gradle.internal.cc.base.serialize.service
import org.gradle.internal.cc.base.serialize.withGradleIsolate
import org.gradle.internal.cc.impl.cacheentry.EntryDetails
import org.gradle.internal.cc.impl.cacheentry.ModelKey
import org.gradle.internal.cc.impl.initialization.ConfigurationCacheStartParameter
import org.gradle.internal.cc.impl.io.safeWrap
import org.gradle.internal.cc.impl.problems.ConfigurationCacheProblems
import org.gradle.internal.cc.impl.serialize.Codecs
import org.gradle.internal.cc.impl.serialize.DefaultClassDecoder
import org.gradle.internal.cc.impl.serialize.DefaultClassEncoder
import org.gradle.internal.encryption.EncryptionService
import org.gradle.internal.extensions.stdlib.useToRun
import org.gradle.internal.hash.HashCode
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.PositionAwareEncoder
import org.gradle.internal.serialize.graph.BeanStateReaderLookup
import org.gradle.internal.serialize.graph.BeanStateWriterLookup
import org.gradle.internal.serialize.graph.CloseableReadContext
import org.gradle.internal.serialize.graph.CloseableWriteContext
import org.gradle.internal.serialize.graph.DefaultReadContext
import org.gradle.internal.serialize.graph.DefaultWriteContext
import org.gradle.internal.serialize.graph.LoggingTracer
import org.gradle.internal.serialize.graph.MutableReadContext
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.Tracer
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.readCollection
import org.gradle.internal.serialize.graph.readFile
import org.gradle.internal.serialize.graph.readList
import org.gradle.internal.serialize.graph.readNonNull
import org.gradle.internal.serialize.graph.runReadOperation
import org.gradle.internal.serialize.graph.runWriteOperation
import org.gradle.internal.serialize.graph.writeCollection
import org.gradle.internal.serialize.graph.writeFile
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.gradle.internal.serialize.kryo.StringDeduplicatingKryoBackedDecoder
import org.gradle.internal.serialize.kryo.StringDeduplicatingKryoBackedEncoder
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.util.Path
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream


@ServiceScope(Scope.Build::class)
internal
class DefaultConfigurationCacheIO internal constructor(
    private val startParameter: ConfigurationCacheStartParameter,
    private val host: ConfigurationCacheHost,
    private val problems: ConfigurationCacheProblems,
    private val scopeRegistryListener: ConfigurationCacheClassLoaderScopeRegistryListener,
    private val beanStateReaderLookup: BeanStateReaderLookup,
    private val beanStateWriterLookup: BeanStateWriterLookup,
    private val eventEmitter: BuildOperationProgressEventEmitter
) : ConfigurationCacheBuildTreeIO, ConfigurationCacheIncludedBuildIO {

    private
    val codecs = codecs()

    private
    val encryptionService by lazy { service<EncryptionService>() }

    override fun writeCacheEntryDetailsTo(
        buildStateRegistry: BuildStateRegistry,
        intermediateModels: Map<ModelKey, BlockAddress>,
        projectMetadata: Map<Path, BlockAddress>,
        sideEffects: List<BlockAddress>,
        stateFile: ConfigurationCacheStateFile
    ) {
        val rootDirs = collectRootDirs(buildStateRegistry)
        writeConfigurationCacheState(stateFile) {
            writeCollection(rootDirs) { writeFile(it) }
            val addressSerializer = BlockAddressSerializer()
            writeCollection(intermediateModels.entries) { entry ->
                writeModelKey(entry.key)
                addressSerializer.write(this, entry.value)
            }
            writeCollection(projectMetadata.entries) { entry ->
                writeString(entry.key.path)
                addressSerializer.write(this, entry.value)
            }
            writeCollection(sideEffects) {
                addressSerializer.write(this, it)
            }
        }
    }

    private
    fun WriteContext.writeModelKey(key: ModelKey) {
        writeNullableString(key.identityPath?.path)
        writeString(key.modelName)
        writeNullableString(key.parameterHash?.toString())
    }

    override fun readCacheEntryDetailsFrom(stateFile: ConfigurationCacheStateFile): EntryDetails? {
        if (!stateFile.exists) {
            return null
        }
        return readConfigurationCacheState(stateFile) {
            val rootDirs = readList { readFile() }
            val addressSerializer = BlockAddressSerializer()
            val intermediateModels = mutableMapOf<ModelKey, BlockAddress>()
            readCollection {
                val modelKey = readModelKey()
                val address = addressSerializer.read(this)
                intermediateModels[modelKey] = address
            }
            val metadata = mutableMapOf<Path, BlockAddress>()
            readCollection {
                val path = Path.path(readString())
                val address = addressSerializer.read(this)
                metadata[path] = address
            }
            val sideEffects = readList {
                addressSerializer.read(this)
            }
            EntryDetails(rootDirs, intermediateModels, metadata, sideEffects)
        }
    }

    private
    fun ReadContext.readModelKey(): ModelKey {
        val path = readNullableString()?.let { Path.path(it) }
        val modelName = readString()
        val parameterHash = readNullableString()?.let(HashCode::fromString)
        return ModelKey(path, modelName, parameterHash)
    }

    private
    fun collectRootDirs(buildStateRegistry: BuildStateRegistry): MutableSet<File> {
        val rootDirs = mutableSetOf<File>()
        buildStateRegistry.visitBuilds { build ->
            rootDirs.add(build.buildRootDir)
        }
        return rootDirs
    }

    /**
     * See [ConfigurationCacheState.writeRootBuildState].
     */
    override fun writeRootBuildStateTo(stateFile: ConfigurationCacheStateFile) =
        writeConfigurationCacheState(stateFile) { cacheState ->
            cacheState.run {
                writeRootBuildState(host.currentBuild)
            }
        }

    override fun readRootBuildStateFrom(
        stateFile: ConfigurationCacheStateFile,
        loadAfterStore: Boolean,
        graph: BuildTreeWorkGraph,
        graphBuilder: BuildTreeWorkGraphBuilder?
    ): Pair<String, BuildTreeWorkGraph.FinalizedGraph> {
        return readConfigurationCacheState(stateFile) { state ->
            state.run {
                readRootBuildState(graph, graphBuilder, loadAfterStore)
            }
        }
    }

    override fun writeIncludedBuildStateTo(stateFile: ConfigurationCacheStateFile, buildTreeState: StoredBuildTreeState) {
        writeConfigurationCacheState(stateFile) { cacheState ->
            cacheState.run {
                writeBuildContent(host.currentBuild, buildTreeState)
            }
        }
    }

    override fun readIncludedBuildStateFrom(stateFile: ConfigurationCacheStateFile, includedBuild: ConfigurationCacheBuild) =
        readConfigurationCacheState(stateFile) { state ->
            state.run {
                readBuildContent(includedBuild)
            }
        }

    private
    fun <T> readConfigurationCacheState(
        stateFile: ConfigurationCacheStateFile,
        action: suspend MutableReadContext.(ConfigurationCacheState) -> T
    ): T {
        return withReadContextFor(stateFile.stateType, stateFile::inputStream) { codecs ->
            ConfigurationCacheState(codecs, stateFile, eventEmitter, host).run {
                action(this)
            }
        }
    }

    private
    fun <T> writeConfigurationCacheState(
        stateFile: ConfigurationCacheStateFile,
        action: suspend WriteContext.(ConfigurationCacheState) -> T
    ): T {
        val (context, codecs) = writeContextFor(stateFile.stateType, stateFile::outputStream) {
            host.currentBuild.gradle.owner.displayName.displayName + " state"
        }
        return context.useToRun {
            runWriteOperation {
                action(ConfigurationCacheState(codecs, stateFile, eventEmitter, host))
            }
        }
    }

    override fun writeModelTo(model: Any, stateFile: ConfigurationCacheStateFile) {
        writeConfigurationCacheState(stateFile) {
            withGradleIsolate(host.currentBuild.gradle, codecs.userTypesCodec()) {
                write(model)
            }
        }
    }

    override fun readModelFrom(stateFile: ConfigurationCacheStateFile): Any {
        return readConfigurationCacheState(stateFile) {
            withGradleIsolate(host.currentBuild.gradle, codecs.userTypesCodec()) {
                readNonNull()
            }
        }
    }

    /**
     * @param profile the unique name associated with the output stream for debugging space usage issues
     */
    override fun writeContextFor(
        stateType: StateType,
        outputStream: () -> OutputStream,
        profile: () -> String
    ): Pair<CloseableWriteContext, Codecs> =
        encoderFor(stateType, outputStream).let { encoder ->
            writeContextFor(
                encoder,
                loggingTracerFor(profile, encoder),
                codecs
            ) to codecs
        }

    private
    fun encoderFor(stateType: StateType, outputStream: () -> OutputStream): PositionAwareEncoder =
        outputStreamFor(stateType, outputStream).let { stream ->
            if (startParameter.isDeduplicatingStrings) StringDeduplicatingKryoBackedEncoder(stream)
            else KryoBackedEncoder(stream)
        }

    private
    fun decoderFor(stateType: StateType, inputStream: () -> InputStream): Decoder =
        inputStreamFor(stateType, inputStream).let { stream ->
            if (startParameter.isDeduplicatingStrings) StringDeduplicatingKryoBackedDecoder(stream)
            else KryoBackedDecoder(stream)
        }

    private
    fun outputStreamFor(stateType: StateType, outputStream: () -> OutputStream) =
        maybeEncrypt(stateType, outputStream, encryptionService::outputStream)

    private
    fun inputStreamFor(stateType: StateType, inputStream: () -> InputStream) =
        maybeEncrypt(stateType, inputStream, encryptionService::inputStream)

    private
    fun <I : Closeable, O : I> maybeEncrypt(stateType: StateType, inner: () -> I, outer: (I) -> O): I =
        if (stateType.encryptable) safeWrap(inner, outer)
        else inner()

    private
    fun loggingTracerFor(profile: () -> String, encoder: PositionAwareEncoder) =
        loggingTracerLogLevel()?.let { level ->
            LoggingTracer(profile(), encoder::getWritePosition, logger, level)
        }

    private
    fun loggingTracerLogLevel(): LogLevel? = when {
        startParameter.isDebug -> LogLevel.LIFECYCLE
        logger.isDebugEnabled -> LogLevel.DEBUG
        else -> null
    }

    override fun <T> runWriteOperation(encoder: Encoder, writeOperation: suspend WriteContext.(codecs: Codecs) -> T): T {
        val (context, codecs) = writeContextFor(encoder)
        return context.runWriteOperation { writeOperation(codecs) }
    }

    private
    fun writeContextFor(encoder: Encoder): Pair<CloseableWriteContext, Codecs> =
        writeContextFor(
            encoder,
            null,
            codecs
        ) to codecs

    override fun <R> withReadContextFor(
        stateType: StateType,
        inputStream: () -> InputStream,
        readOperation: suspend MutableReadContext.(Codecs) -> R
    ): R =
        readContextFor(decoderFor(stateType, inputStream))
            .let { (context, codecs) ->
                context.useToRun {
                    runReadOperation {
                        readOperation(codecs)
                    }.also {
                        finish()
                    }
                }
            }

    override fun <T> runReadOperation(decoder: Decoder, readOperation: suspend ReadContext.(codecs: Codecs) -> T): T {
        val (context, codecs) = readContextFor(decoder)
        return context.runReadOperation { readOperation(codecs) }
    }

    private
    fun readContextFor(
        decoder: Decoder,
    ) = readContextFor(decoder, codecs) to codecs

    private
    fun writeContextFor(
        encoder: Encoder,
        tracer: Tracer?,
        codecs: Codecs
    ): CloseableWriteContext = DefaultWriteContext(
        codecs.userTypesCodec(),
        encoder,
        beanStateWriterLookup,
        logger,
        tracer,
        problems,
        DefaultClassEncoder(scopeRegistryListener),
    )

    private
    fun readContextFor(
        decoder: Decoder,
        codecs: Codecs
    ): CloseableReadContext = DefaultReadContext(
        codecs.userTypesCodec(),
        decoder,
        beanStateReaderLookup,
        logger,
        problems,
        DefaultClassDecoder()
    )

    private
    fun codecs(): Codecs =
        Codecs(
            directoryFileTreeFactory = service(),
            fileCollectionFactory = service(),
            artifactSetConverter = service(),
            fileLookup = service(),
            propertyFactory = service(),
            filePropertyFactory = service(),
            fileResolver = service(),
            objectFactory = service(),
            instantiator = service(),
            fileSystemOperations = service(),
            taskNodeFactory = service(),
            ordinalGroupFactory = service(),
            inputFingerprinter = service(),
            buildOperationRunner = service(),
            classLoaderHierarchyHasher = service(),
            isolatableFactory = service(),
            managedFactoryRegistry = service(),
            parameterScheme = service(),
            actionScheme = service(),
            attributesFactory = service(),
            valueSourceProviderFactory = service(),
            calculatedValueContainerFactory = service(),
            patternSetFactory = factory(),
            fileOperations = service(),
            fileFactory = service(),
            includedTaskGraph = service(),
            buildStateRegistry = service(),
            documentationRegistry = service(),
            javaSerializationEncodingLookup = service(),
            flowProviders = service(),
            transformStepNodeFactory = service(),
        )

    private
    inline fun <reified T : Any> service() =
        host.service<T>()

    private
    inline fun <reified T> factory() =
        host.factory(T::class.java)
}
