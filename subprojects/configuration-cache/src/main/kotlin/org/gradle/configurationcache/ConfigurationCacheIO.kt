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

import org.gradle.configurationcache.extensions.useToRun
import org.gradle.configurationcache.fingerprint.ConfigurationCacheFingerprint
import org.gradle.configurationcache.problems.ConfigurationCacheProblems
import org.gradle.configurationcache.serialization.DefaultReadContext
import org.gradle.configurationcache.serialization.DefaultWriteContext
import org.gradle.configurationcache.serialization.LoggingTracer
import org.gradle.configurationcache.serialization.Tracer
import org.gradle.configurationcache.serialization.beans.BeanConstructors
import org.gradle.configurationcache.serialization.codecs.Codecs
import org.gradle.configurationcache.serialization.readCollectionInto
import org.gradle.configurationcache.serialization.readNonNull
import org.gradle.configurationcache.serialization.runReadOperation
import org.gradle.configurationcache.serialization.runWriteOperation
import org.gradle.configurationcache.serialization.withGradleIsolate
import org.gradle.configurationcache.serialization.writeCollection
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream


@ServiceScope(Scopes.Gradle::class)
class ConfigurationCacheIO internal constructor(
    private val host: DefaultConfigurationCache.Host,
    private val problems: ConfigurationCacheProblems,
    private val scopeRegistryListener: ConfigurationCacheClassLoaderScopeRegistryListener,
    private val beanConstructors: BeanConstructors
) {

    /**
     * See [ConfigurationCacheState.writeRootBuildState].
     */
    internal
    fun writeRootBuildStateTo(stateFile: ConfigurationCacheStateFile): Set<File> =
        writeConfigurationCacheState(stateFile) { cacheState ->
            cacheState.run {
                writeRootBuildState(host.currentBuild)
            }
        }

    internal
    fun readRootBuildStateFrom(stateFile: ConfigurationCacheStateFile) {
        readConfigurationCacheState(stateFile) { state ->
            state.run {
                readRootBuildState(host::createBuild)
            }
        }
    }

    internal
    fun writeIncludedBuildStateTo(stateFile: ConfigurationCacheStateFile, buildTreeState: StoredBuildTreeState) {
        writeConfigurationCacheState(stateFile) { cacheState ->
            cacheState.run {
                writeBuildState(host.currentBuild, buildTreeState)
            }
        }
    }

    internal
    fun readIncludedBuildStateFrom(stateFile: ConfigurationCacheStateFile, includedBuild: ConfigurationCacheBuild) =
        readConfigurationCacheState(stateFile) { state ->
            state.run {
                readBuildState(includedBuild)
            }
        }

    private
    fun <T> readConfigurationCacheState(
        stateFile: ConfigurationCacheStateFile,
        action: suspend DefaultReadContext.(ConfigurationCacheState) -> T
    ): T {
        return withReadContextFor(stateFile.inputStream()) { codecs ->
            ConfigurationCacheState(codecs, stateFile).run {
                action(this)
            }
        }
    }

    private
    fun <T> writeConfigurationCacheState(
        stateFile: ConfigurationCacheStateFile,
        action: suspend DefaultWriteContext.(ConfigurationCacheState) -> T
    ): T {
        val build = host.currentBuild
        val (context, codecs) = writerContextFor(stateFile.outputStream(), build.gradle.owner.displayName.displayName + " state")
        return context.useToRun {
            runWriteOperation {
                action(ConfigurationCacheState(codecs, stateFile))
            }
        }
    }

    internal
    fun writeModelTo(model: Any, stateFile: ConfigurationCacheStateFile) {
        writeConfigurationCacheState(stateFile) {
            withGradleIsolate(host.currentBuild.gradle, codecs().userTypesCodec) {
                write(model)
            }
        }
    }

    internal
    fun readModelFrom(stateFile: ConfigurationCacheStateFile): Any {
        return readConfigurationCacheState(stateFile) {
            withGradleIsolate(host.currentBuild.gradle, codecs().userTypesCodec) {
                readNonNull()
            }
        }
    }

    internal
    fun writerContextFor(outputStream: OutputStream, profile: String): Pair<DefaultWriteContext, Codecs> =
        codecs().let { codecs ->
            KryoBackedEncoder(outputStream).let { encoder ->
                writeContextFor(
                    encoder,
                    if (logger.isDebugEnabled) LoggingTracer(profile, encoder::getWritePosition, logger)
                    else null,
                    codecs
                ) to codecs
            }
        }

    internal
    fun <R> withReadContextFor(
        inputStream: InputStream,
        readOperation: suspend DefaultReadContext.(Codecs) -> R
    ): R =
        codecs().let { codecs ->
            KryoBackedDecoder(inputStream).use { decoder ->
                readContextFor(decoder, codecs).run {
                    initClassLoader(javaClass.classLoader)
                    runReadOperation {
                        readOperation(codecs)
                    }
                }
            }
        }

    private
    fun writeContextFor(
        encoder: Encoder,
        tracer: Tracer?,
        codecs: Codecs
    ) = DefaultWriteContext(
        codecs.userTypesCodec,
        encoder,
        scopeRegistryListener,
        logger,
        tracer,
        problems
    )

    private
    fun readContextFor(
        decoder: KryoBackedDecoder,
        codecs: Codecs
    ) = DefaultReadContext(
        codecs.userTypesCodec,
        decoder,
        service(),
        beanConstructors,
        logger,
        problems
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
            instantiator = service(),
            listenerManager = service(),
            taskNodeFactory = service(),
            inputFingerprinter = service(),
            buildOperationExecutor = service(),
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
            documentationRegistry = service()
        )

    private
    inline fun <reified T> service() =
        host.service<T>()

    private
    inline fun <reified T> factory() =
        host.factory(T::class.java)
}


internal
fun writeConfigurationCacheFingerprintHeaderTo(outputStream: OutputStream, header: ConfigurationCacheFingerprint.Header) {
    val buildRootDirs = header.includedBuildRootDirs
    if (buildRootDirs.isEmpty()) {
        outputStream.writeInt(0)
        return
    }
    ByteArrayOutputStream().let { bos ->
        writeFileSetTo(bos, buildRootDirs)
        outputStream.writeInt(bos.size())
        bos.writeTo(outputStream)
    }
}


internal
fun readConfigurationCacheFingerprintHeaderFrom(inputStream: InputStream): ConfigurationCacheFingerprint.Header? {
    val headerSize = inputStream.readInt()
    if (headerSize == 0) {
        return null
    }

    val headerBytes = ByteArray(headerSize)
    require(inputStream.read(headerBytes) == headerSize)

    return ConfigurationCacheFingerprint.Header(
        readFileSetFrom(ByteArrayInputStream(headerBytes))
    )
}


private
fun writeFileSetTo(outputStream: OutputStream, files: Set<File>) {
    KryoBackedEncoder(outputStream).useToRun {
        writeCollection(files) { file ->
            writeString(file.path)
        }
    }
}


private
fun readFileSetFrom(inputStream: InputStream) =
    KryoBackedDecoder(inputStream).run {
        readCollectionInto(::LinkedHashSet) {
            File(readString())
        }
    }


private
fun OutputStream.writeInt(i: Int) = DataOutputStream(this).writeInt(i)


private
fun InputStream.readInt() = DataInputStream(this).readInt()
