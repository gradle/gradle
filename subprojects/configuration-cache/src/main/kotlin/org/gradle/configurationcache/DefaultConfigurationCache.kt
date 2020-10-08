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

import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logging
import org.gradle.configurationcache.ConfigurationCacheRepository.CheckedFingerprint
import org.gradle.configurationcache.extensions.unsafeLazy
import org.gradle.configurationcache.fingerprint.ConfigurationCacheFingerprintController
import org.gradle.configurationcache.fingerprint.InvalidationReason
import org.gradle.configurationcache.initialization.ConfigurationCacheStartParameter
import org.gradle.configurationcache.problems.ConfigurationCacheProblems
import org.gradle.configurationcache.serialization.DefaultReadContext
import org.gradle.configurationcache.serialization.DefaultWriteContext
import org.gradle.configurationcache.serialization.IsolateOwner
import org.gradle.configurationcache.serialization.LoggingTracer
import org.gradle.configurationcache.serialization.MutableIsolateContext
import org.gradle.configurationcache.serialization.Tracer
import org.gradle.configurationcache.serialization.beans.BeanConstructors
import org.gradle.configurationcache.serialization.codecs.Codecs
import org.gradle.configurationcache.serialization.runReadOperation
import org.gradle.configurationcache.serialization.runWriteOperation
import org.gradle.configurationcache.serialization.withIsolate
import org.gradle.initialization.ConfigurationCache
import org.gradle.initialization.GradlePropertiesController
import org.gradle.internal.Factory
import org.gradle.internal.classpath.Instrumented
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.gradle.kotlin.dsl.support.useToRun
import org.gradle.util.IncubationLogger
import java.io.File
import java.io.OutputStream


class DefaultConfigurationCache internal constructor(
    private val host: Host,
    private val startParameter: ConfigurationCacheStartParameter,
    private val cacheRepository: ConfigurationCacheRepository,
    private val cacheKey: ConfigurationCacheKey,
    private val problems: ConfigurationCacheProblems,
    private val systemPropertyListener: SystemPropertyAccessListener,
    private val scopeRegistryListener: ConfigurationCacheClassLoaderScopeRegistryListener,
    private val cacheFingerprintController: ConfigurationCacheFingerprintController,
    private val beanConstructors: BeanConstructors,
    private val gradlePropertiesController: GradlePropertiesController,
    private val relevantProjectsRegistry: RelevantProjectsRegistry
) : ConfigurationCache {

    interface Host {

        val currentBuild: VintageGradleBuild

        fun createBuild(rootProjectName: String): ConfigurationCacheBuild

        fun <T> service(serviceType: Class<T>): T

        fun <T> factory(serviceType: Class<T>): Factory<T>
    }

    override fun canLoad(): Boolean = when {
        !isConfigurationCacheEnabled -> {
            false
        }
        startParameter.recreateCache -> {
            logBootstrapSummary("Recreating configuration cache")
            false
        }
        startParameter.isRefreshDependencies -> {
            logBootstrapSummary(
                "Calculating task graph as configuration cache cannot be reused due to {}",
                "--refresh-dependencies"
            )
            false
        }
        startParameter.isWriteDependencyLocks -> {
            logBootstrapSummary(
                "Calculating task graph as configuration cache cannot be reused due to {}",
                "--write-locks"
            )
            false
        }
        startParameter.isUpdateDependencyLocks -> {
            logBootstrapSummary(
                "Calculating task graph as configuration cache cannot be reused due to {}",
                "--update-locks"
            )
            false
        }
        else -> {
            val checkedFingerprint = cacheRepository.useForFingerprintCheck(
                cacheKey.string,
                this::checkFingerprint
            )
            when (checkedFingerprint) {
                is CheckedFingerprint.NotFound -> {
                    logBootstrapSummary(
                        "Calculating task graph as no configuration cache is available for tasks: {}",
                        startParameter.requestedTaskNames.joinToString(" ")
                    )
                    false
                }
                is CheckedFingerprint.Invalid -> {
                    logBootstrapSummary(
                        "Calculating task graph as configuration cache cannot be reused because {}.",
                        checkedFingerprint.reason
                    )
                    false
                }
                is CheckedFingerprint.Valid -> {
                    logBootstrapSummary("Reusing configuration cache.")
                    true
                }
            }
        }
    }

    override fun prepareForConfiguration() {

        if (!isConfigurationCacheEnabled) return

        startCollectingCacheFingerprint()
        Instrumented.setListener(systemPropertyListener)
    }

    override fun save() {

        if (!isConfigurationCacheEnabled) {
            // No need to hold onto the `ClassLoaderScope` tree
            // if we are not writing it.
            scopeRegistryListener.dispose()
            return
        }

        // TODO - fingerprint should be collected until the state file has been written, as user code can run during this process
        // Moving this is currently broken because the Jar task queries provider values when serializing the manifest file tree and this
        // can cause the provider value to incorrectly be treated as a task graph input
        Instrumented.discardListener()
        stopCollectingCacheFingerprint()

        buildOperationExecutor.withStoreOperation {
            cacheRepository.useForStore(cacheKey.string) { layout ->
                problems.storing {
                    invalidateConfigurationCacheState(layout)
                }
                try {
                    writeConfigurationCacheFiles(layout)
                } catch (error: ConfigurationCacheError) {
                    // Invalidate state on serialization errors
                    invalidateConfigurationCacheState(layout)
                    problems.failingBuildDueToSerializationError()
                    throw error
                } finally {
                    cacheFingerprintController.stop()
                }
            }
        }
    }

    override fun load() {

        require(isConfigurationCacheEnabled)

        problems.loading()

        // No need to record the `ClassLoaderScope` tree
        // when loading the task graph.
        scopeRegistryListener.dispose()

        buildOperationExecutor.withLoadOperation {
            cacheRepository.useForStateLoad(cacheKey.string) { stateFile ->
                readConfigurationCacheState(stateFile)
            }
        }
    }

    private
    fun writeConfigurationCacheFiles(layout: ConfigurationCacheRepository.Layout) {
        writeConfigurationCacheState(layout.state)
        writeConfigurationCacheFingerprint(layout.fingerprint)
    }

    private
    fun writeConfigurationCacheState(stateFile: File) {
        service<ProjectStateRegistry>().withMutableStateOfAllProjects {
            withWriteContextFor(stateFile, "state") {
                configurationCacheState().run {
                    writeState()
                }
            }
        }
    }

    private
    fun readConfigurationCacheState(stateFile: File) {
        withReadContextFor(stateFile) {
            configurationCacheState().run {
                readState()
            }
        }
    }

    private
    fun configurationCacheState() =
        ConfigurationCacheState(codecs(), host, relevantProjectsRegistry)

    private
    fun startCollectingCacheFingerprint() {
        cacheFingerprintController.startCollectingFingerprint {
            cacheFingerprintWriterContextFor(it)
        }
    }

    private
    fun stopCollectingCacheFingerprint() {
        cacheFingerprintController.stopCollectingFingerprint()
    }

    private
    fun writeConfigurationCacheFingerprint(fingerprintFile: File) =
        cacheFingerprintController.commitFingerprintTo(fingerprintFile)

    private
    fun cacheFingerprintWriterContextFor(outputStream: OutputStream) =
        writerContextFor(outputStream, "fingerprint").apply {
            push(IsolateOwner.OwnerHost(host), codecs().userTypesCodec)
        }

    private
    fun checkFingerprint(fingerprintFile: File): InvalidationReason? {
        loadGradleProperties()
        return checkConfigurationCacheFingerprintFile(fingerprintFile)
    }

    private
    fun checkConfigurationCacheFingerprintFile(fingerprintFile: File): InvalidationReason? =
        withReadContextFor(fingerprintFile) {
            withHostIsolate {
                cacheFingerprintController.run {
                    checkFingerprint()
                }
            }
        }

    private
    fun loadGradleProperties() {
        gradlePropertiesController.loadGradlePropertiesFrom(
            startParameter.settingsDirectory
        )
    }

    private
    fun invalidateConfigurationCacheState(layout: ConfigurationCacheRepository.Layout) {
        layout.fingerprint.delete()
    }

    private
    fun withWriteContextFor(file: File, profile: String, writeOperation: suspend DefaultWriteContext.() -> Unit) {
        writerContextFor(file.outputStream(), profile).useToRun {
            runWriteOperation(writeOperation)
        }
    }

    private
    fun writerContextFor(outputStream: OutputStream, profile: String) =
        KryoBackedEncoder(outputStream).let { encoder ->
            writeContextFor(
                encoder,
                if (logger.isDebugEnabled) LoggingTracer(profile, encoder::getWritePosition, logger)
                else null
            )
        }

    private
    fun <R> withReadContextFor(file: File, readOperation: suspend DefaultReadContext.() -> R): R =
        KryoBackedDecoder(file.inputStream()).use { decoder ->
            readContextFor(decoder).run {
                initClassLoader(javaClass.classLoader)
                runReadOperation(readOperation)
            }
        }

    private
    fun writeContextFor(
        encoder: Encoder,
        tracer: Tracer?
    ) = DefaultWriteContext(
        codecs().userTypesCodec,
        encoder,
        scopeRegistryListener,
        logger,
        tracer,
        problems
    )

    private
    fun readContextFor(
        decoder: KryoBackedDecoder
    ) = DefaultReadContext(
        codecs().userTypesCodec,
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
            fileFactory = service()
        )

    private
    inline fun <T : MutableIsolateContext, R> T.withHostIsolate(block: T.() -> R): R =
        withIsolate(IsolateOwner.OwnerHost(host), codecs().userTypesCodec) {
            block()
        }

    private
    fun logBootstrapSummary(message: String, vararg args: Any?) {
        if (!startParameter.isQuiet) {
            IncubationLogger.incubatingFeatureUsed("Configuration cache")
        }
        log(message, *args)
    }

    private
    fun log(message: String, vararg args: Any?) {
        logger.log(configurationCacheLogLevel, message, *args)
    }

    private
    val buildOperationExecutor: BuildOperationExecutor
        get() = service()

    private
    inline fun <reified T> service() =
        host.service<T>()

    private
    inline fun <reified T> factory() =
        host.factory(T::class.java)

    // Skip configuration cache for buildSrc for now.
    private
    val isConfigurationCacheEnabled: Boolean by unsafeLazy {
        startParameter.isEnabled && host.currentBuild.gradle.isRootBuild
    }

    private
    val configurationCacheLogLevel: LogLevel
        get() = when (startParameter.isQuiet) {
            true -> LogLevel.INFO
            else -> LogLevel.LIFECYCLE
        }
}


internal
inline fun <reified T> DefaultConfigurationCache.Host.service(): T =
    service(T::class.java)


private
val logger = Logging.getLogger(DefaultConfigurationCache::class.java)
