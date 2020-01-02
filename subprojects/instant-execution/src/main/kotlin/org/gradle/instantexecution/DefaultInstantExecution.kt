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
import org.gradle.api.internal.provider.DefaultValueSourceProviderFactory
import org.gradle.api.internal.provider.ValueSourceProviderFactory
import org.gradle.api.internal.provider.sources.SystemPropertyValueSource
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.execution.plan.Node
import org.gradle.initialization.InstantExecution
import org.gradle.instantexecution.extensions.uncheckedCast
import org.gradle.instantexecution.serialization.DefaultReadContext
import org.gradle.instantexecution.serialization.DefaultWriteContext
import org.gradle.instantexecution.serialization.IsolateOwner
import org.gradle.instantexecution.serialization.MutableIsolateContext
import org.gradle.instantexecution.serialization.beans.BeanConstructors
import org.gradle.instantexecution.serialization.codecs.Codecs
import org.gradle.instantexecution.serialization.codecs.WorkNodeCodec
import org.gradle.instantexecution.serialization.readCollection
import org.gradle.instantexecution.serialization.withIsolate
import org.gradle.instantexecution.serialization.writeCollection
import org.gradle.internal.build.event.BuildEventListenerRegistryInternal
import org.gradle.internal.hash.HashUtil
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.gradle.internal.vfs.VirtualFileSystem
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.util.GradleVersion
import org.gradle.util.Path
import java.io.File
import java.nio.file.Files
import java.util.ArrayList
import java.util.SortedSet
import java.util.TreeSet
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine


class DefaultInstantExecution internal constructor(
    private val host: Host,
    private val scopeRegistryListener: InstantExecutionClassLoaderScopeRegistryListener,
    private val beanConstructors: BeanConstructors,
    private val valueSourceProviderFactory: ValueSourceProviderFactory,
    private val virtualFileSystem: VirtualFileSystem
) : InstantExecution {

    interface Host {

        val skipLoadingStateReason: String?

        val currentBuild: ClassicModeBuild

        fun createBuild(rootProjectName: String): InstantExecutionBuild

        fun <T> getService(serviceType: Class<T>): T

        fun getSystemProperty(propertyName: String): String?

        val rootDir: File

        val requestedTaskNames: List<String>
    }

    override fun canExecuteInstantaneously(): Boolean = when {
        !isInstantExecutionEnabled -> {
            false
        }
        host.skipLoadingStateReason != null -> {
            log(
                "Calculating task graph as instant execution cache cannot be reused due to {}",
                host.skipLoadingStateReason
            )
            false
        }
        !instantExecutionFingerprintFile.isFile -> {
            log(
                "Calculating task graph as no instant execution cache is available for tasks: {}",
                host.requestedTaskNames.joinToString(" ")
            )
            false
        }
        else -> {
            val fingerprintChangedReason = checkFingerprint()
            when {
                fingerprintChangedReason != null -> {
                    log(
                        "Calculating task graph as instant execution cache cannot be reused because {}.",
                        fingerprintChangedReason
                    )
                    false
                }
                else -> {
                    log(
                        "Reusing instant execution cache. This is not guaranteed to work in any way."
                    )
                    true
                }
            }
        }
    }

    override fun prepareForBuildLogicExecution() {

        if (!isInstantExecutionEnabled) return

        attachBuildLogicInputsCollector()
    }

    override fun saveScheduledWork() {

        if (!isInstantExecutionEnabled) {
            // No need to hold onto the `ClassLoaderScope` tree
            // if we are not writing it.
            scopeRegistryListener.dispose()
            return
        }

        detachBuildLogicInputsCollector()

        buildOperationExecutor.withStoreOperation {

            val report = instantExecutionReport()
            val instantExecutionException = report.withExceptionHandling {

                instantExecutionStateFile.createParentDirectories()

                host.getService(ProjectStateRegistry::class.java).withLenientState {
                    withWriteContextFor(instantExecutionStateFile, report) {
                        encodeScheduledWork()
                    }
                }
                withWriteContextFor(instantExecutionFingerprintFile, report) {
                    encodeFingerprint()
                }
            }

            // Discard the state file on errors
            if (instantExecutionException != null) {
                discardInstantExecutionState()
                throw instantExecutionException
            }
        }
    }

    override fun loadScheduledWork() {

        require(isInstantExecutionEnabled)

        // No need to record the `ClassLoaderScope` tree
        // when loading the task graph.
        scopeRegistryListener.dispose()

        buildOperationExecutor.withLoadOperation {
            withReadContextFor(instantExecutionStateFile) {
                decodeScheduledWork()
            }
        }
    }

    private
    suspend fun DefaultWriteContext.encodeScheduledWork() {
        val build = host.currentBuild
        writeString(build.rootProject.name)

        writeGradleState(build.gradle)

        val scheduledNodes = build.scheduledWork
        writeRelevantProjectsFor(scheduledNodes)

        WorkNodeCodec(build.gradle, codecs.internalTypesCodec).run {
            writeWork(scheduledNodes)
        }
    }

    private
    suspend fun DefaultReadContext.decodeScheduledWork() {
        val rootProjectName = readString()
        val build = host.createBuild(rootProjectName)

        readGradleState(build.gradle)

        readRelevantProjects(build)

        build.autoApplyPlugins()
        build.registerProjects()

        initProjectProvider(build::getProject)

        val scheduledNodes = WorkNodeCodec(build.gradle, codecs.internalTypesCodec).run {
            readWork()
        }
        build.scheduleNodes(scheduledNodes)
    }

    private
    suspend fun DefaultWriteContext.encodeFingerprint() {
        withHostIsolate {
            instantExecutionInputs!!.run {
                writeCollection(inputFiles)
                writeCollection(obtainedValues)
            }
        }
    }

    private
    fun checkFingerprint(): InvalidationReason? =
        withReadContextFor(instantExecutionFingerprintFile) {
            withHostIsolate {
                checkFingerprintOfInputFiles()
                    ?: checkFingerprintOfObtainedValues()
            }
        }

    private
    suspend fun DefaultReadContext.checkFingerprintOfInputFiles(): InvalidationReason? {
        readCollection {
            val (inputFile, hashCode) = read()!!.uncheckedCast<InstantExecutionCacheInputs.InputFile>()
            if (hashCodeOf(inputFile) != hashCode) {
                // TODO: log some debug info
                return "a configuration file has changed"
            }
        }
        return null
    }

    private
    suspend fun DefaultReadContext.checkFingerprintOfObtainedValues(): InvalidationReason? {
        readCollection {
            val obtainedValue = readObtainedValue()
            checkFingerprintValueIsUpToDate(obtainedValue)?.let { reason ->
                return reason
            }
        }
        return null
    }

    private
    fun hashCodeOf(inputFile: File) = virtualFileSystem.hashCodeOf(inputFile)

    private
    suspend fun DefaultReadContext.readObtainedValue(): ObtainedValue =
        read()!!.uncheckedCast()

    private
    fun checkFingerprintValueIsUpToDate(obtainedValue: ObtainedValue): InvalidationReason? = obtainedValue.run {
        when (valueSourceType) {
            SystemPropertyValueSource::class.java -> {
                // Special case system properties to get them from the host because
                // this check happens too early in the process, before the system properties
                // passed in the command line have been propagated.
                val propertyName = valueSourceParameters
                    .uncheckedCast<SystemPropertyValueSource.Parameters>()
                    .propertyName
                    .get()
                if (value.get() != systemProperty(propertyName)) {
                    "system property '$propertyName' has changed"
                } else {
                    null
                }
            }
            else -> {
                val valueSource = instantiateValueSourceOf(this)
                if (value.get() != valueSource.obtain()) {
                    "a build logic input has changed"
                } else {
                    null
                }
            }
        }
    }

    private
    fun instantiateValueSourceOf(obtainedValue: ObtainedValue): ValueSource<Any, ValueSourceParameters> =
        obtainedValue.run {
            (valueSourceProviderFactory as DefaultValueSourceProviderFactory).instantiateValueSource(
                valueSourceType,
                valueSourceParametersType,
                valueSourceParameters
            )
        }

    private
    fun attachBuildLogicInputsCollector() {
        InstantExecutionCacheInputs(virtualFileSystem).also {
            instantExecutionInputs = it
            valueSourceProviderFactory.addListener(it)
        }
    }

    private
    fun detachBuildLogicInputsCollector() {
        instantExecutionInputs.let {
            require(it != null)
            valueSourceProviderFactory.removeListener(it)
        }
    }

    private
    var instantExecutionInputs: InstantExecutionCacheInputs? = null

    private
    fun instantExecutionReport() = InstantExecutionReport(
        reportOutputDir,
        logger,
        maxProblems(),
        failOnProblems()
    )

    private
    fun discardInstantExecutionState() {
        instantExecutionFingerprintFile.delete()
    }

    private
    fun withWriteContextFor(file: File, report: InstantExecutionReport, writeOperation: suspend DefaultWriteContext.() -> Unit) {
        KryoBackedEncoder(file.outputStream()).use { encoder ->
            writeContextFor(encoder, report).run {
                runToCompletion {
                    writeOperation()
                }
            }
        }
    }

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
        encoder: Encoder,
        report: InstantExecutionReport
    ) = DefaultWriteContext(
        codecs.userTypesCodec,
        encoder,
        scopeRegistryListener,
        logger,
        report::add
    )

    private
    fun readContextFor(decoder: KryoBackedDecoder) = DefaultReadContext(
        codecs.userTypesCodec,
        decoder,
        service(),
        beanConstructors,
        logger
    )

    private
    val codecs: Codecs by lazy {
        Codecs(
            directoryFileTreeFactory = service(),
            fileCollectionFactory = service(),
            fileLookup = service(),
            filePropertyFactory = service(),
            fileResolver = service(),
            instantiator = service(),
            listenerManager = service(),
            projectStateRegistry = service(),
            taskNodeFactory = service(),
            fingerprinterRegistry = service(),
            projectFinder = service(),
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
            valueSourceProviderFactory = service()
        )
    }

    private
    suspend fun DefaultWriteContext.writeGradleState(gradle: Gradle) {
        withGradleIsolate(gradle) {
            val eventListenerRegistry = service<BuildEventListenerRegistryInternal>()
            writeCollection(eventListenerRegistry.subscriptions)
        }
    }

    private
    suspend fun DefaultReadContext.readGradleState(gradle: Gradle) {
        withGradleIsolate(gradle) {
            val eventListenerRegistry = service<BuildEventListenerRegistryInternal>()
            readCollection {
                val provider = read()!!.uncheckedCast<Provider<OperationCompletionListener>>()
                eventListenerRegistry.subscribe(provider)
            }
        }
    }

    private
    inline fun <T : MutableIsolateContext, R> T.withGradleIsolate(gradle: Gradle, block: T.() -> R): R =
        withIsolate(IsolateOwner.OwnerGradle(gradle), codecs.userTypesCodec) {
            block()
        }

    private
    inline fun <T : MutableIsolateContext, R> T.withHostIsolate(block: T.() -> R): R =
        withIsolate(IsolateOwner.OwnerHost(host), codecs.userTypesCodec) {
            block()
        }

    private
    fun Encoder.writeRelevantProjectsFor(nodes: List<Node>) {
        writeCollection(fillTheGapsOf(relevantProjectPathsFor(nodes))) { projectPath ->
            writeString(projectPath.path)
        }
    }

    private
    fun Decoder.readRelevantProjects(build: InstantExecutionBuild) {
        readCollection {
            val projectPath = readString()
            build.createProject(projectPath)
        }
    }

    private
    fun relevantProjectPathsFor(nodes: List<Node>): SortedSet<Path> =
        nodes.mapNotNullTo(TreeSet()) { node ->
            node.owningProject
                ?.takeIf { it.parent != null }
                ?.path
                ?.let(Path::path)
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
    fun File.createParentDirectories() {
        Files.createDirectories(parentFile.toPath())
    }

    private
    val instantExecutionFingerprintFile by lazy {
        instantExecutionStateFile.run {
            resolveSibling("$name.fingerprint")
        }
    }

    private
    val instantExecutionStateFile by lazy {
        val currentGradleVersion = GradleVersion.current().version
        val cacheDir = File(host.rootDir, ".instant-execution-state/$currentGradleVersion").absoluteFile
        val baseName = compactMD5For(host.requestedTaskNames)
        val cacheFileName = "$baseName.bin"
        File(cacheDir, cacheFileName)
    }

    private
    val reportOutputDir by lazy {
        instantExecutionStateFile.run {
            resolveSibling(nameWithoutExtension)
        }
    }

    // Skip instant execution for buildSrc for now. Should instead collect up the inputs of its tasks and treat as task graph cache inputs
    private
    val isInstantExecutionEnabled: Boolean by lazy {
        systemProperty(SystemProperties.isEnabled)?.toBoolean() ?: false && !host.currentBuild.buildSrc
    }

    private
    val instantExecutionLogLevel: LogLevel
        get() = if (systemProperty(SystemProperties.isQuiet)?.toBoolean() == true) LogLevel.INFO else LogLevel.LIFECYCLE

    private
    fun maxProblems(): Int =
        systemProperty(SystemProperties.maxProblems)
            ?.let(Integer::valueOf)
            ?: 512

    private
    fun failOnProblems(): Boolean =
        systemProperty(SystemProperties.failOnProblems)
            ?.toBoolean()
            ?: false

    private
    fun systemProperty(propertyName: String) =
        host.getSystemProperty(propertyName)

    private
    fun compactMD5For(taskNames: List<String>) =
        HashUtil.createCompactMD5(taskNames.joinToString("/"))
}


private
typealias InvalidationReason = String


inline fun <reified T> DefaultInstantExecution.Host.service(): T =
    getService(T::class.java)


internal
fun fillTheGapsOf(paths: SortedSet<Path>): List<Path> {
    val pathsWithoutGaps = ArrayList<Path>(paths.size)
    var index = 0
    paths.forEach { path ->
        var parent = path.parent
        var added = 0
        while (parent !== null && parent !in pathsWithoutGaps) {
            pathsWithoutGaps.add(index, parent)
            added += 1
            parent = parent.parent
        }
        pathsWithoutGaps.add(path)
        added += 1
        index += added
    }
    return pathsWithoutGaps
}


private
val logger = Logging.getLogger(DefaultInstantExecution::class.java)


/**
 * [Starts][startCoroutine] the suspending [block], asserts it runs
 * to completion and returns its result.
 */
internal
fun <R> runToCompletion(block: suspend () -> R): R {
    var completion: Result<R>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) {
        completion = it
    })
    return completion.let {
        require(it != null) {
            "Coroutine didn't run to completion."
        }
        it.getOrThrow()
    }
}
