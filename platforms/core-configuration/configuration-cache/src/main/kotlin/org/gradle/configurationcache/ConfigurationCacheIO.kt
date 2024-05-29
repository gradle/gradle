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

package org.gradle.configurationcache

import org.gradle.api.logging.LogLevel
import org.gradle.cache.internal.streams.BlockAddress
import org.gradle.cache.internal.streams.BlockAddressSerializer
import org.gradle.configurationcache.cacheentry.EntryDetails
import org.gradle.configurationcache.cacheentry.ModelKey
import org.gradle.internal.extensions.stdlib.useToRun
import org.gradle.configurationcache.initialization.ConfigurationCacheStartParameter
import org.gradle.configurationcache.problems.ConfigurationCacheProblems
import org.gradle.internal.serialize.graph.Codec
import org.gradle.configurationcache.serialization.DefaultClassDecoder
import org.gradle.configurationcache.serialization.DefaultClassEncoder
import org.gradle.internal.serialize.graph.DefaultReadContext
import org.gradle.internal.serialize.graph.DefaultWriteContext
import org.gradle.internal.serialize.graph.LoggingTracer
import org.gradle.internal.serialize.graph.Tracer
import org.gradle.configurationcache.serialization.beans.DefaultBeanStateReaderLookup
import org.gradle.configurationcache.serialization.beans.DefaultBeanStateWriterLookup
import org.gradle.configurationcache.serialization.codecs.Codecs
import org.gradle.internal.serialize.graph.readCollection
import org.gradle.internal.serialize.graph.readFile
import org.gradle.internal.serialize.graph.readList
import org.gradle.internal.serialize.graph.readNonNull
import org.gradle.internal.serialize.graph.runReadOperation
import org.gradle.internal.serialize.graph.runWriteOperation
import org.gradle.configurationcache.serialization.withGradleIsolate
import org.gradle.internal.serialize.graph.writeCollection
import org.gradle.internal.serialize.graph.writeFile
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.buildtree.BuildTreeWorkGraph
import org.gradle.internal.hash.HashCode
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.util.Path
import java.io.File
import java.io.InputStream
import java.io.OutputStream


@ServiceScope(Scope.Build::class)
class ConfigurationCacheIO internal constructor(
    private val startParameter: ConfigurationCacheStartParameter,
    private val host: DefaultConfigurationCache.Host,
    private val problems: ConfigurationCacheProblems,
    private val scopeRegistryListener: ConfigurationCacheClassLoaderScopeRegistryListener,
    private val beanStateReaderLookup: DefaultBeanStateReaderLookup,
    private val beanStateWriterLookup: DefaultBeanStateWriterLookup,
    private val eventEmitter: BuildOperationProgressEventEmitter
) {
    private
    val codecs = codecs()

    private
    val encryptionService by lazy { service<EncryptionService>() }

    internal
    fun writeCacheEntryDetailsTo(
        buildStateRegistry: BuildStateRegistry,
        intermediateModels: Map<ModelKey, BlockAddress>,
        projectMetadata: Map<Path, BlockAddress>,
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
        }
    }

    private
    fun DefaultWriteContext.writeModelKey(key: ModelKey) {
        writeNullableString(key.identityPath?.path)
        writeString(key.modelName)
        writeNullableString(key.parameterHash?.toString())
    }

    internal
    fun readCacheEntryDetailsFrom(stateFile: ConfigurationCacheStateFile): EntryDetails? {
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
            EntryDetails(rootDirs, intermediateModels, metadata)
        }
    }

    private
    fun DefaultReadContext.readModelKey(): ModelKey {
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
    internal
    fun writeRootBuildStateTo(stateFile: ConfigurationCacheStateFile) =
        writeConfigurationCacheState(stateFile) { cacheState ->
            cacheState.run {
                writeRootBuildState(host.currentBuild)
            }
        }

    internal
    fun readRootBuildStateFrom(
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

    internal
    fun writeIncludedBuildStateTo(stateFile: ConfigurationCacheStateFile, buildTreeState: StoredBuildTreeState) {
        writeConfigurationCacheState(stateFile) { cacheState ->
            cacheState.run {
                writeBuildContent(host.currentBuild, buildTreeState)
            }
        }
    }

    internal
    fun readIncludedBuildStateFrom(stateFile: ConfigurationCacheStateFile, includedBuild: ConfigurationCacheBuild) =
        readConfigurationCacheState(stateFile) { state ->
            state.run {
                readBuildContent(includedBuild)
            }
        }

    private
    fun <T> readConfigurationCacheState(
        stateFile: ConfigurationCacheStateFile,
        action: suspend DefaultReadContext.(ConfigurationCacheState) -> T
    ): T {
        return withReadContextFor(encryptionService.inputStream(stateFile.stateType, stateFile::inputStream)) { codecs ->
            ConfigurationCacheState(codecs, stateFile, eventEmitter, host).run {
                action(this)
            }
        }
    }

    private
    fun <T> writeConfigurationCacheState(
        stateFile: ConfigurationCacheStateFile,
        action: suspend DefaultWriteContext.(ConfigurationCacheState) -> T
    ): T {
        val (context, codecs) = writerContextFor(encryptionService.outputStream(stateFile.stateType, stateFile::outputStream)) {
            host.currentBuild.gradle.owner.displayName.displayName + " state"
        }
        return context.useToRun {
            runWriteOperation {
                action(ConfigurationCacheState(codecs, stateFile, eventEmitter, host))
            }
        }
    }

    internal
    fun writeModelTo(model: Any, stateFile: ConfigurationCacheStateFile) {
        writeConfigurationCacheState(stateFile) {
            withGradleIsolate(host.currentBuild.gradle, codecs.userTypesCodec()) {
                write(model)
            }
        }
    }

    internal
    fun readModelFrom(stateFile: ConfigurationCacheStateFile): Any {
        return readConfigurationCacheState(stateFile) {
            withGradleIsolate(host.currentBuild.gradle, codecs.userTypesCodec()) {
                readNonNull()
            }
        }
    }

    /**
     * @param profile the unique name associated with the output stream for debugging space usage issues
     */
    internal
    fun writerContextFor(outputStream: OutputStream, profile: () -> String): Pair<DefaultWriteContext, Codecs> =
        KryoBackedEncoder(outputStream).let { encoder ->
            writeContextFor(
                encoder,
                loggingTracerFor(profile, encoder),
                codecs
            ) to codecs
        }

    private
    fun loggingTracerFor(profile: () -> String, encoder: KryoBackedEncoder) =
        loggingTracerLogLevel()?.let { level ->
            LoggingTracer(profile(), encoder::getWritePosition, logger, level)
        }

    private
    fun loggingTracerLogLevel(): LogLevel? = when {
        startParameter.isDebug -> LogLevel.LIFECYCLE
        logger.isDebugEnabled -> LogLevel.DEBUG
        else -> null
    }

    internal
    fun writerContextFor(encoder: Encoder): Pair<DefaultWriteContext, Codecs> =
        writeContextFor(
            encoder,
            null,
            codecs
        ) to codecs

    internal
    fun <R> withReadContextFor(
        inputStream: InputStream,
        readOperation: suspend DefaultReadContext.(Codecs) -> R
    ): R =
        readerContextFor(inputStream).let { (context, codecs) ->
            context.use {
                context.run {
                    initClassLoader(javaClass.classLoader)
                    runReadOperation {
                        readOperation(codecs)
                    }.also {
                        finish()
                    }
                }
            }
        }

    private
    fun readerContextFor(
        inputStream: InputStream,
    ) = readerContextFor(KryoBackedDecoder(inputStream))

    internal
    fun readerContextFor(
        decoder: Decoder,
    ) =
        readContextFor(decoder, codecs).apply {
            initClassLoader(javaClass.classLoader)
        } to codecs

    private
    fun writeContextFor(
        encoder: Encoder,
        tracer: Tracer?,
        codecs: Codecs
    ) = writeContextFor(
        encoder,
        tracer,
        codecs.userTypesCodec()
    )

    private
    fun writeContextFor(
        encoder: Encoder,
        tracer: Tracer?,
        codec: Codec<Any?>
    ) = DefaultWriteContext(
        codec,
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
    ) = readContextFor(decoder, codecs.userTypesCodec())

    private
    fun readContextFor(
        decoder: Decoder,
        codec: Codec<Any?>
    ) = DefaultReadContext(
        codec,
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
    inline fun <reified T> service() =
        host.service<T>()

    private
    inline fun <reified T> factory() =
        host.factory(T::class.java)
}
