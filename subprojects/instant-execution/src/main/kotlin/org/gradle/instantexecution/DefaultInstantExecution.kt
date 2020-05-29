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
import org.gradle.util.GradleVersion
import org.gradle.util.IncubationLogger
import java.io.File
import java.io.OutputStream
import java.nio.file.Files


class DefaultInstantExecution internal constructor(
    private val host: Host,
    private val startParameter: InstantExecutionStartParameter,
    private val problems: InstantExecutionProblems,
    private val systemPropertyListener: SystemPropertyAccessListener,
    private val scopeRegistryListener: InstantExecutionClassLoaderScopeRegistryListener,
    private val cacheFingerprintController: InstantExecutionCacheFingerprintController,
    private val beanConstructors: BeanConstructors,
    private val gradlePropertiesController: GradlePropertiesController
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
        !instantExecutionFingerprintFile.isFile -> {
            logBootstrapSummary(
                "Calculating task graph as no configuration cache is available for tasks: {}",
                startParameter.requestedTaskNames.joinToString(" ")
            )
            false
        }
        else -> {
            val fingerprintChangedReason = checkFingerprint()
            when {
                fingerprintChangedReason != null -> {
                    logBootstrapSummary(
                        "Calculating task graph as configuration cache cannot be reused because {}.",
                        fingerprintChangedReason
                    )
                    false
                }
                else -> {
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

        Instrumented.discardListener()
        stopCollectingCacheFingerprint()

        buildOperationExecutor.withStoreOperation {
            try {
                writeInstantExecutionFiles()
            } catch (error: InstantExecutionError) {
                // Invalidate state on problems that fail the build
                invalidateInstantExecutionState()
                problems.failingBuildDueToSerializationError()
                throw error
            }
        }
    }

    override fun loadScheduledWork() {

        require(isInstantExecutionEnabled)

        // No need to record the `ClassLoaderScope` tree
        // when loading the task graph.
        scopeRegistryListener.dispose()

        buildOperationExecutor.withLoadOperation {
            readInstantExecutionState()
        }
    }

    private
    fun writeInstantExecutionFiles() {
        instantExecutionStateFile.createParentDirectories()
        writeInstantExecutionState()
        writeInstantExecutionCacheFingerprint()
    }

    private
    fun writeInstantExecutionState() {
        service<ProjectStateRegistry>().withLenientState {
            withWriteContextFor(instantExecutionStateFile) {
                InstantExecutionState(codecs, host).run {
                    writeState()
                }
            }
        }
    }

    private
    fun readInstantExecutionState() {
        withReadContextFor(instantExecutionStateFile) {
            InstantExecutionState(codecs, host).run {
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
    fun writeInstantExecutionCacheFingerprint() {
        cacheFingerprintController.commitFingerprintTo(
            instantExecutionFingerprintFile
        )
    }

    private
    fun cacheFingerprintWriterContextFor(outputStream: OutputStream) =
        writerContextFor(outputStream).apply {
            push(IsolateOwner.OwnerHost(host), codecs.userTypesCodec)
        }

    private
    fun checkFingerprint(): InvalidationReason? {
        loadGradleProperties()
        return checkInstantExecutionFingerprintFile()
    }

    private
    fun checkInstantExecutionFingerprintFile(): InvalidationReason? =
        withReadContextFor(instantExecutionFingerprintFile) {
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
    fun invalidateInstantExecutionState() {
        instantExecutionFingerprintFile.delete()
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

    private
    fun File.createParentDirectories() {
        Files.createDirectories(parentFile.toPath())
    }

    private
    val instantExecutionFingerprintFile by unsafeLazy {
        instantExecutionStateFile.run {
            resolveSibling("$name.fingerprint")
        }
    }

    private
    val instantExecutionStateFile by unsafeLazy {
        val cacheDir = absoluteFile(".gradle/configuration-cache/${currentGradleVersion()}")
        val baseName = startParameter.instantExecutionCacheKey
        val cacheFileName = "$baseName.bin"
        File(cacheDir, cacheFileName)
    }

    private
    fun currentGradleVersion(): String =
        GradleVersion.current().version

    private
    fun absoluteFile(path: String) =
        File(rootDirectory, path).absoluteFile

    // Skip instant execution for buildSrc for now. Should instead collect up the inputs of its tasks and treat as task graph cache inputs
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

    private
    val rootDirectory
        get() = startParameter.rootDirectory
}


internal
inline fun <reified T> DefaultInstantExecution.Host.service(): T =
    service(T::class.java)


private
val logger = Logging.getLogger(DefaultInstantExecution::class.java)
