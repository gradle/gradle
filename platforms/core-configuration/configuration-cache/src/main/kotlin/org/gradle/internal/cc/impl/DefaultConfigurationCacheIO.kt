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
import org.gradle.initialization.ClassLoaderScopeRegistry
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.buildtree.BuildTreeWorkGraph
import org.gradle.internal.cc.base.logger
import org.gradle.internal.cc.base.serialize.IsolateOwners
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
import org.gradle.internal.cc.impl.serialize.DefaultSharedObjectDecoder
import org.gradle.internal.cc.impl.serialize.DefaultSharedObjectEncoder
import org.gradle.internal.cc.impl.serialize.ParallelStringDecoder
import org.gradle.internal.cc.impl.serialize.ParallelStringEncoder
import org.gradle.internal.encryption.EncryptionService
import org.gradle.internal.hash.HashCode
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.PositionAwareEncoder
import org.gradle.internal.serialize.codecs.core.IsolateContextSource
import org.gradle.internal.serialize.graph.BeanStateReaderLookup
import org.gradle.internal.serialize.graph.BeanStateWriterLookup
import org.gradle.internal.serialize.graph.CloseableReadContext
import org.gradle.internal.serialize.graph.CloseableWriteContext
import org.gradle.internal.serialize.graph.DefaultReadContext
import org.gradle.internal.serialize.graph.DefaultWriteContext
import org.gradle.internal.serialize.graph.InlineSharedObjectDecoder
import org.gradle.internal.serialize.graph.InlineSharedObjectEncoder
import org.gradle.internal.serialize.graph.InlineStringDecoder
import org.gradle.internal.serialize.graph.InlineStringEncoder
import org.gradle.internal.serialize.graph.LoggingTracer
import org.gradle.internal.serialize.graph.MutableReadContext
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.SpecialDecoders
import org.gradle.internal.serialize.graph.SpecialEncoders
import org.gradle.internal.serialize.graph.SharedObjectDecoder
import org.gradle.internal.serialize.graph.SharedObjectEncoder
import org.gradle.internal.serialize.graph.StringDecoder
import org.gradle.internal.serialize.graph.StringEncoder
import org.gradle.internal.serialize.graph.Tracer
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.readCollection
import org.gradle.internal.serialize.graph.readFile
import org.gradle.internal.serialize.graph.readList
import org.gradle.internal.serialize.graph.readNonNull
import org.gradle.internal.serialize.graph.readWith
import org.gradle.internal.serialize.graph.runReadOperation
import org.gradle.internal.serialize.graph.runWriteOperation
import org.gradle.internal.serialize.graph.writeCollection
import org.gradle.internal.serialize.graph.writeFile
import org.gradle.internal.serialize.graph.writeWith
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
    private val beanStateReaderLookup: BeanStateReaderLookup,
    private val beanStateWriterLookup: BeanStateWriterLookup,
    private val eventEmitter: BuildOperationProgressEventEmitter,
    private val classLoaderScopeRegistryListener: ConfigurationCacheClassLoaderScopeRegistryListener,
    private val classLoaderScopeRegistry: ClassLoaderScopeRegistry,
    private val instantiatorFactory: InstantiatorFactory
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

    override fun WriteContext.writeIncludedBuildStateTo(stateFile: ConfigurationCacheStateFile, buildTreeState: StoredBuildTreeState) =
        // we share the string encoder with the root build, but not the shared object encoder
        withSharedObjectEncoderFor(stateFile, currentStringEncoder) { sharedObjectEncoder ->
            writeConfigurationCacheStateWithSpecialEncoders(SpecialEncoders(currentStringEncoder, sharedObjectEncoder), stateFile) { cacheState ->
                cacheState.run {
                    writeBuildContent(host.currentBuild, buildTreeState)
                }
            }
        }

    override fun ReadContext.readIncludedBuildStateFrom(stateFile: ConfigurationCacheStateFile, includedBuild: ConfigurationCacheBuild): CachedBuildState =
        withSharedObjectDecoderFor(stateFile, currentStringDecoder) { sharedObjectDecoder ->
            readConfigurationCacheStateWithSpecialDecoders(SpecialDecoders(currentStringDecoder, sharedObjectDecoder), stateFile) { state ->
                state.run {
                    readBuildContent(includedBuild)
                }
            }
        }

    private
    fun <T> readConfigurationCacheState(
        stateFile: ConfigurationCacheStateFile,
        action: suspend MutableReadContext.(ConfigurationCacheState) -> T
    ): T = withStringDecoderFor(stateFile) { stringDecoder ->
        withSharedObjectDecoderFor(stateFile, stringDecoder) { sharedObjectDecoder ->
            readConfigurationCacheStateWithSpecialDecoders(SpecialDecoders(stringDecoder, sharedObjectDecoder), stateFile, action)
        }
    }

    private
    fun <T> writeConfigurationCacheState(
        stateFile: ConfigurationCacheStateFile,
        action: suspend WriteContext.(ConfigurationCacheState) -> T
    ): T =
        withStringEncoderFor(stateFile) { stringEncoder ->
            withSharedObjectEncoderFor(stateFile, stringEncoder) { sharedObjectEncoder ->
                writeConfigurationCacheStateWithSpecialEncoders(SpecialEncoders(stringEncoder, sharedObjectEncoder), stateFile, action)
            }
        }

    private
    fun stringEncoderFor(stringsFile: ConfigurationCacheStateFile): StringEncoder =
        if (isUsingParallelStringDeduplicationStrategy(stringsFile))
            outputStreamFor(stringsFile.stateType, stringsFile::outputStream).let(::ParallelStringEncoder)
        else
            InlineStringEncoder

    private
    fun stringDecoderFor(stringsFile: ConfigurationCacheStateFile): StringDecoder =
        if (isUsingParallelStringDeduplicationStrategy(stringsFile))
            inputStreamFor(stringsFile.stateType, stringsFile::inputStream).let(::ParallelStringDecoder)
        else
            InlineStringDecoder

    private
    fun sharedObjectEncoderFor(baseFile: ConfigurationCacheStateFile, globalsFile: ConfigurationCacheStateFile, stringEncoder: StringEncoder): SharedObjectEncoder =
        isUsingObjectSharingStrategy(baseFile).let { deduplicate ->
            if (deduplicate) {
                val (globalContext, _) = writeContextFor(globalsFile, SpecialEncoders(stringEncoder)) { "global values" }
                globalContext.push(IsolateOwners.OwnerGradle(host.currentBuild.gradle))
                DefaultSharedObjectEncoder(globalContext)
            } else {
                InlineSharedObjectEncoder
            }
        }

    private
    fun sharedObjectDecoder(baseFile: ConfigurationCacheStateFile, globalsFile: ConfigurationCacheStateFile, stringDecoder: StringDecoder): SharedObjectDecoder =
        isUsingObjectSharingStrategy(baseFile).let { deduplicate ->
            if (deduplicate) {
                // Create a context that honors global value duplication
                // but uses an inline global value decoder
                val (globalContext, _) = readContextFor(globalsFile, SpecialDecoders(stringDecoder))
                globalContext.push(IsolateOwners.OwnerGradle(host.currentBuild.gradle))
                DefaultSharedObjectDecoder(globalContext)
            } else {
                InlineSharedObjectDecoder
            }
        }

    private
    fun <T> withStringEncoderFor(stateFile: ConfigurationCacheStateFile, action: (StringEncoder) -> T): T =
        stringsFileFor(stateFile).let { stringsFile ->
            stringEncoderFor(stringsFile).use(action)
        }

    private
    fun <T> withStringDecoderFor(stateFile: ConfigurationCacheStateFile, action: (StringDecoder) -> T): T =
        stringsFileFor(stateFile).let { stringsFile ->
            stringDecoderFor(stringsFile).use(action)
        }

    private
    fun <T> withSharedObjectEncoderFor(baseFile: ConfigurationCacheStateFile, stringEncoder: StringEncoder, action: (SharedObjectEncoder) -> T): T =
        sharedObjectFileFor(baseFile).let { globalsFile ->
            sharedObjectEncoderFor(baseFile, globalsFile, stringEncoder).use(action)
        }

    private
    fun <T> withSharedObjectDecoderFor(baseFile: ConfigurationCacheStateFile, stringDecoder: StringDecoder, action: (SharedObjectDecoder) -> T): T =
        sharedObjectFileFor(baseFile).let { globalsFile ->
            sharedObjectDecoder(baseFile, globalsFile, stringDecoder).use(action)
        }

    private
    fun stringsFileFor(stateFile: ConfigurationCacheStateFile) =
        stateFile.relatedStateFile(Path.path(".strings"))

    private
    fun sharedObjectFileFor(stateFile: ConfigurationCacheStateFile) =
        stateFile.stateFileForSharedObjects()

    private
    fun <T> readConfigurationCacheStateWithSpecialDecoders(
        specialDecoders: SpecialDecoders,
        stateFile: ConfigurationCacheStateFile,
        action: suspend MutableReadContext.(ConfigurationCacheState) -> T
    ) = withReadContextFor(stateFile, specialDecoders) { codecs ->
        ConfigurationCacheState(codecs, stateFile, ChildContextSource(stateFile), eventEmitter, host).run {
            action(this)
        }
    }

    private
    fun <T> writeConfigurationCacheStateWithSpecialEncoders(
        specialEncoders: SpecialEncoders,
        stateFile: ConfigurationCacheStateFile,
        action: suspend WriteContext.(ConfigurationCacheState) -> T
    ): T {
        val profile = {
            host.currentBuild.gradle.owner.displayName.displayName + " state"
        }
        return withWriteContextFor(stateFile, profile, specialEncoders) { codecs ->
            action(ConfigurationCacheState(codecs, stateFile, ChildContextSource(stateFile), eventEmitter, host))
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

    private
    fun writeContextFor(
        stateFile: ConfigurationCacheStateFile,
        specialEncoders: SpecialEncoders,
        profile: () -> String
    ) = writeContextFor(stateFile.stateFile.name, stateFile.stateType, stateFile::outputStream, profile, specialEncoders)

    /**
     * @param profile the unique name associated with the output stream for debugging space usage issues
     */
    override fun writeContextFor(
        name: String,
        stateType: StateType,
        outputStream: () -> OutputStream,
        profile: () -> String,
        specialEncoders: SpecialEncoders,
    ): Pair<CloseableWriteContext, Codecs> =
        encoderFor(stateType, outputStream).let { encoder ->
            writeContextFor(
                name,
                encoder,
                loggingTracerFor(profile, encoder),
                codecs,
                specialEncoders
            ) to codecs
        }

    private
    fun encoderFor(stateType: StateType, outputStream: () -> OutputStream): PositionAwareEncoder =
        outputStreamFor(stateType, outputStream).let { stream ->
            if (isUsingSequentialStringDeduplicationStrategy(stateType)) StringDeduplicatingKryoBackedEncoder(stream)
            else KryoBackedEncoder(stream)
        }

    private
    fun decoderFor(stateType: StateType, inputStream: () -> InputStream): Decoder =
        inputStreamFor(stateType, inputStream).let { stream ->
            if (isUsingSequentialStringDeduplicationStrategy(stateType)) StringDeduplicatingKryoBackedDecoder(stream)
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

    /**
     * For the [work graph state][StateType.Work], we use the parallel string deduplication strategy since it spans multiple files,
     * for everything else we use the sequential, per encoder/decoder, deduplication strategy.
     */
    private
    fun isUsingParallelStringDeduplicationStrategy(stateFile: ConfigurationCacheStateFile) =
        stateFile.stateType == StateType.Work && startParameter.isDeduplicatingStrings

    private
    fun isUsingSequentialStringDeduplicationStrategy(stateType: StateType) =
        stateType != StateType.Work && startParameter.isDeduplicatingStrings

    /**
     * For the [work graph state][StateType.Work], we use the parallel global value deduplication strategy since it spans multiple files,
     * for everything else we use the sequential, per encoder/decoder, deduplication strategy.
     */
    private
    fun isUsingObjectSharingStrategy(stateFile: ConfigurationCacheStateFile) =
        stateFile.stateType == StateType.Work && startParameter.isSharingObjects

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
            "unnamed",
            encoder,
            null,
            codecs
        ) to codecs

    override fun <R> withReadContextFor(
        name: String,
        stateType: StateType,
        inputStream: () -> InputStream,
        specialDecoders: SpecialDecoders,
        readOperation: suspend MutableReadContext.(Codecs) -> R
    ): R =
        readContextFor(name, stateType, inputStream, specialDecoders)
            .let { (context, codecs) ->
                withReadContextFor(context, codecs, readOperation)
            }

    override fun <R> withReadContextFor(
        readContext: CloseableReadContext,
        codecs: Codecs,
        readOperation: suspend MutableReadContext.(Codecs) -> R
    ): R =
        readContext.readWith(codecs, readOperation)

    override fun <R> withWriteContextFor(
        name: String,
        stateType: StateType,
        outputStream: () -> OutputStream,
        profile: () -> String,
        specialEncoders: SpecialEncoders,
        writeOperation: suspend WriteContext.(Codecs) -> R
    ): R =
        writeContextFor(name, stateType, outputStream, profile, specialEncoders)
            .let { (context, codecs) ->
                context.writeWith(codecs, writeOperation)
            }

    private fun readContextFor(
        stateFile: ConfigurationCacheStateFile,
        specialDecoders: SpecialDecoders = SpecialDecoders()
    ) = readContextFor(stateFile.stateFile.name, stateFile.stateType, stateFile::inputStream, specialDecoders)

    private fun readContextFor(
        name: String,
        stateType: StateType,
        inputStream: () -> InputStream,
        specialDecoders: SpecialDecoders
    ) = readContextFor(name, decoderFor(stateType, inputStream), specialDecoders)

    override fun <T> runReadOperation(decoder: Decoder, readOperation: suspend ReadContext.(codecs: Codecs) -> T): T {
        val (context, codecs) = readContextFor("unnamed", decoder, SpecialDecoders())
        return context.runReadOperation { readOperation(codecs) }
    }

    private
    fun readContextFor(
        name: String? = null,
        decoder: Decoder,
        specialDecoders: SpecialDecoders
    ) = readContextFor(name, decoder, codecs, specialDecoders) to codecs

    private
    fun writeContextFor(
        name: String? = null,
        encoder: Encoder,
        tracer: Tracer?,
        codecs: Codecs,
        specialEncoders: SpecialEncoders = SpecialEncoders()
    ): CloseableWriteContext = DefaultWriteContext(
        name,
        codecs.userTypesCodec(),
        encoder,
        beanStateWriterLookup,
        logger,
        tracer,
        problems,
        classEncoder(),
        specialEncoders = specialEncoders
    )

    private
    fun readContextFor(
        name: String? = null,
        decoder: Decoder,
        codecs: Codecs,
        specialDecoders: SpecialDecoders
    ): CloseableReadContext = DefaultReadContext(
        name,
        codecs.userTypesCodec(),
        decoder,
        beanStateReaderLookup,
        logger,
        problems,
        classDecoder(),
        specialDecoders
    )

    private
    fun classEncoder() =
        DefaultClassEncoder(classLoaderScopeRegistryListener)

    private
    fun classDecoder() =
        DefaultClassDecoder(
            classLoaderScopeRegistry.coreAndPluginsScope,
            instantiatorFactory.decorateScheme().deserializationInstantiator()
        )

    /**
     * Provides R/W isolate contexts based on some other context.
     */
    inner class ChildContextSource(private val baseFile: ConfigurationCacheStateFile) : IsolateContextSource {
        override fun readContextFor(baseContext: ReadContext, path: Path): CloseableReadContext =
            baseFile.relatedStateFile(path).let {
                readContextFor(it, SpecialDecoders(baseContext.currentStringDecoder, baseContext.currentSharedObjectDecoder)).also { (subContext, subCodecs) ->
                    subContext.push(baseContext.isolate.owner, subCodecs.internalTypesCodec())
                }.first
            }

        override fun writeContextFor(baseContext: WriteContext, path: Path): CloseableWriteContext =
            baseFile.relatedStateFile(path).let {
                writeContextFor(it, SpecialEncoders(baseContext.currentStringEncoder, baseContext.currentSharedObjectEncoder)) { "child '$path' state" }.also { (subContext, subCodecs) ->
                    subContext.push(baseContext.isolate.owner, subCodecs.internalTypesCodec())
                }.first
            }
    }

    private
    val WriteContext.currentStringEncoder: StringEncoder
        get() {
            require(this is DefaultWriteContext)
            return this.stringEncoder
        }

    private
    val ReadContext.currentStringDecoder: StringDecoder
        get() {
            require(this is DefaultReadContext)
            return this.stringDecoder
        }

    private
    val WriteContext.currentSharedObjectEncoder: SharedObjectEncoder
        get() {
            require(this is DefaultWriteContext)
            return this.sharedObjectEncoder
        }

    private
    val ReadContext.currentSharedObjectDecoder: SharedObjectDecoder
        get() {
            require(this is DefaultReadContext)
            return this.sharedObjectDecoder
        }

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
            parallelStore = startParameter.isParallelStore,
            parallelLoad = startParameter.isParallelLoad,
            problems = service(),
        )

    private
    inline fun <reified T : Any> service() =
        host.service<T>()

    private
    inline fun <reified T> factory() =
        host.factory(T::class.java)
}
