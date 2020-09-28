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

import org.gradle.configurationcache.problems.ConfigurationCacheProblems
import org.gradle.configurationcache.serialization.DefaultReadContext
import org.gradle.configurationcache.serialization.DefaultWriteContext
import org.gradle.configurationcache.serialization.LoggingTracer
import org.gradle.configurationcache.serialization.Tracer
import org.gradle.configurationcache.serialization.beans.BeanConstructors
import org.gradle.configurationcache.serialization.codecs.Codecs
import org.gradle.configurationcache.serialization.runReadOperation
import org.gradle.configurationcache.serialization.runWriteOperation
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.gradle.kotlin.dsl.support.useToRun
import java.io.File
import java.io.OutputStream


class ConfigurationCacheIO internal constructor(
    private val host: DefaultConfigurationCache.Host,
    private val problems: ConfigurationCacheProblems,
    private val scopeRegistryListener: ConfigurationCacheClassLoaderScopeRegistryListener,
    private val beanConstructors: BeanConstructors
) {

    internal
    fun writeRootConfigurationCacheState(stateFile: File) {
        writeConfigurationCacheState(stateFile) { cacheState ->
            cacheState.run {
                writeRootBuildState(host.currentBuild)
            }
        }
    }

    internal
    fun writeIncludedConfigurationCacheState(stateFile: File) {
        writeConfigurationCacheState(stateFile) { cacheState ->
            cacheState.run {
                writeBuildState(host.currentBuild)
            }
        }
    }

    private
    fun writeConfigurationCacheState(stateFile: File, action: suspend DefaultWriteContext.(ConfigurationCacheState) -> Unit) {
        val build = host.currentBuild
        val (context, codecs) = writerContextFor(stateFile.outputStream(), build.gradle.rootProject.name + " state")
        context.useToRun {
            runWriteOperation {
                action(ConfigurationCacheState(codecs, stateFile))
            }
        }
    }

    internal
    fun readConfigurationCacheState(stateFile: File) {
        withReadContextFor(stateFile) { codecs ->
            ConfigurationCacheState(codecs, stateFile).run {
                readRootBuildState(host::createBuild)
            }
        }
    }

    internal
    fun readIncludedConfigurationCacheState(stateFile: File, build: ConfigurationCacheBuild) {
        withReadContextFor(stateFile) { codecs ->
            ConfigurationCacheState(codecs, stateFile).run {
                readBuildState(build)
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
    fun <R> withReadContextFor(file: File, readOperation: suspend DefaultReadContext.(Codecs) -> R): R =
        codecs().let { codecs ->
            KryoBackedDecoder(file.inputStream()).use { decoder ->
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
            fileLookup = service(),
            propertyFactory = service(),
            filePropertyFactory = service(),
            fileResolver = service(),
            instantiator = service(),
            listenerManager = service(),
            taskNodeFactory = service(),
            fingerprinterRegistry = service(),
            buildOperationExecutor = service(),
            classLoaderHierarchyHasher = service(),
            isolatableFactory = service(),
            valueSnapshotter = service(),
            buildServiceRegistry = service(),
            managedFactoryRegistry = service(),
            parameterScheme = service(),
            actionScheme = service(),
            attributesFactory = service(),
            transformListener = service(),
            transformationNodeRegistry = service(),
            valueSourceProviderFactory = service(),
            patternSetFactory = factory(),
            fileOperations = service(),
            fileFactory = service(),
            includedTaskGraph = service()
        )

    private
    inline fun <reified T> service() =
        host.service<T>()

    private
    inline fun <reified T> factory() =
        host.factory(T::class.java)
}
