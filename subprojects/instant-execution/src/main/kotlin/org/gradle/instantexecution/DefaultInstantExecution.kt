/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution

import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logging
import org.gradle.initialization.GradlePropertiesController
import org.gradle.initialization.InstantExecution
import org.gradle.instantexecution.InstantExecutionCache.CheckedFingerprint
import org.gradle.instantexecution.coroutines.runToCompletion
import org.gradle.instantexecution.extensions.unsafeLazy
import org.gradle.instantexecution.fingerprint.InstantExecutionCacheFingerprintController
import org.gradle.instantexecution.fingerprint.InvalidationReason
import org.gradle.instantexecution.initialization.InstantExecutionStartParameter
import org.gradle.instantexecution.problems.InstantExecutionProblems
import org.gradle.instantexecution.serialization.DefaultReadContext
import org.gradle.instantexecution.serialization.DefaultWriteContext
import org.gradle.instantexecution.serialization.IsolateOwner
import org.gradle.instantexecution.serialization.MutableIsolateContext
import org.gradle.instantexecution.serialization.beans.BeanConstructors
import org.gradle.instantexecution.serialization.codecs.Codecs
import org.gradle.instantexecution.serialization.runWriteOperation
import org.gradle.instantexecution.serialization.withIsolate
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


class DefaultInstantExecution internal constructor(
    private val host: Host,
    private val startParameter: InstantExecutionStartParameter,
    private val cache: InstantExecutionCache,
    private val cacheKey: InstantExecutionCacheKey,
    private val problems: InstantExecutionProblems,
    private val systemPropertyListener: SystemPropertyAccessListener,
    private val scopeRegistryListener: InstantExecutionClassLoaderScopeRegistryListener,
    private val cacheFingerprintController: InstantExecutionCacheFingerprintController,
    private val beanConstructors: BeanConstructors,
    private val gradlePropertiesController: GradlePropertiesController,
    private val relevantProjectsRegistry: RelevantProjectsRegistry
) : InstantExecution {

    interface Host {

        val currentBuild: VintageGradleBuild

        fun createBuild(rootProjectName: String): InstantExecutionBuild

        fun <T> service(serviceType: Class<T>): T

        fun <T> factory(serviceType: Class<T>): Factory<T>
    }

    override fun canExecuteInstantaneously(): Boolean = when {
        !isInstantExecutionEnabled -> {
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
        else -> {
            val checkedFingerprint = cache.useForFingerprintCheck(
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

    override fun prepareForBuildLogicExecution() {

        if (!isInstantExecutionEnabled) return

        startCollectingCacheFingerprint()
        Instrumented.setListener(systemPropertyListener)
    }

    override fun saveScheduledWork() {

        if (!isInstantExecutionEnabled) {
            // No need to hold onto the `ClassLoaderScope` tree
            // if we are not writing it.
            scopeRegistryListener.dispose()
            return
        }

        problems.storing()

        // TODO - fingerprint should be collected until the state file has been written, as user code can run during this process
        // Moving this is currently broken because the Jar task queries provider values when serializing the manifest file tree and this
        // can cause the provider value to incorrectly be treated as a task graph input
        Instrumented.discardListener()
        stopCollectingCacheFingerprint()

        buildOperationExecutor.withStoreOperation {
            cache.useForStore(cacheKey.string) { layout ->
                try {
                    writeInstantExecutionFiles(layout)
                } catch (error: InstantExecutionError) {
                    // Invalidate state on problems that fail the build
                    invalidateInstantExecutionState(layout)
                    problems.failingBuildDueToSerializationError()
                    throw error
                }
            }
        }
    }

    override fun loadScheduledWork() {

        require(isInstantExecutionEnabled)

        problems.loading()

        // No need to record the `ClassLoaderScope` tree
        // when loading the task graph.
        scopeRegistryListener.dispose()

        buildOperationExecutor.withLoadOperation {
            cache.useForStateLoad(cacheKey.string) { stateFile ->
                readInstantExecutionState(stateFile)
            }
        }
    }

    private
    fun writeInstantExecutionFiles(layout: InstantExecutionCache.Layout) {
        writeInstantExecutionState(layout.state)
        writeInstantExecutionCacheFingerprint(layout.fingerprint)
    }

    private
    fun writeInstantExecutionState(stateFile: File) {
        service<ProjectStateRegistry>().withLenientState {
            withWriteContextFor(stateFile) {
                InstantExecutionState(codecs, host, relevantProjectsRegistry).run {
                    writeState()
                }
            }
        }
    }

    private
    fun readInstantExecutionState(stateFile: File) {
        withReadContextFor(stateFile) {
            InstantExecutionState(codecs, host, relevantProjectsRegistry).run {
                readState()
            }
        }
    }

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
    fun writeInstantExecutionCacheFingerprint(fingerprintFile: File) =
        cacheFingerprintController.commitFingerprintTo(fingerprintFile)

    private
    fun cacheFingerprintWriterContextFor(outputStream: OutputStream) =
        writerContextFor(outputStream).apply {
            push(IsolateOwner.OwnerHost(host), codecs.userTypesCodec)
        }

    private
    fun checkFingerprint(fingerprintFile: File): InvalidationReason? {
        loadGradleProperties()
        return checkInstantExecutionFingerprintFile(fingerprintFile)
    }

    private
    fun checkInstantExecutionFingerprintFile(fingerprintFile: File): InvalidationReason? =
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
    fun invalidateInstantExecutionState(layout: InstantExecutionCache.Layout) {
        layout.fingerprint.delete()
    }

    private
    fun withWriteContextFor(file: File, writeOperation: suspend DefaultWriteContext.() -> Unit) {
        writerContextFor(file.outputStream()).useToRun {
            runWriteOperation(writeOperation)
        }
    }

    private
    fun writerContextFor(outputStream: OutputStream) =
        writeContextFor(KryoBackedEncoder(outputStream))

    private
    fun <R> withReadContextFor(file: File, readOperation: suspend DefaultReadContext.() -> R): R =
        KryoBackedDecoder(file.inputStream()).use { decoder ->
            readContextFor(decoder).run {
                initClassLoader(javaClass.classLoader)
                runToCompletion {
                    readOperation()
                }
            }
        }

    private
    fun writeContextFor(
        encoder: Encoder
    ) = DefaultWriteContext(
        codecs.userTypesCodec,
        encoder,
        scopeRegistryListener,
        logger,
        problems
    )

    private
    fun readContextFor(decoder: KryoBackedDecoder) = DefaultReadContext(
        codecs.userTypesCodec,
        decoder,
        service(),
        beanConstructors,
        logger,
        problems
    )

    private
    val codecs: Codecs by unsafeLazy {
        Codecs(
            directoryFileTreeFactory = service(),
            fileCollectionFactory = service(),
            fileLookup = service(),
            propertyFactory = service(),
            filePropertyFactory = service(),
            fileResolver = service(),
            instantiator = service(),
            listenerManager = service(),
            projectStateRegistry = service(),
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
            valueSourceProviderFactory = service(),
            patternSetFactory = factory(),
            fileOperations = service(),
            fileSystem = service(),
            fileFactory = service()
        )
    }

    private
    inline fun <T : MutableIsolateContext, R> T.withHostIsolate(block: T.() -> R): R =
        withIsolate(IsolateOwner.OwnerHost(host), codecs.userTypesCodec) {
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
        logger.log(instantExecutionLogLevel, message, *args)
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

    // Skip instant execution for buildSrc for now.
    private
    val isInstantExecutionEnabled: Boolean by unsafeLazy {
        startParameter.isEnabled && !host.currentBuild.buildSrc
    }

    private
    val instantExecutionLogLevel: LogLevel
        get() = when (startParameter.isQuiet) {
            true -> LogLevel.INFO
            else -> LogLevel.LIFECYCLE
        }
}


internal
inline fun <reified T> DefaultInstantExecution.Host.service(): T =
    service(T::class.java)


private
val logger = Logging.getLogger(DefaultInstantExecution::class.java)
