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
import org.gradle.api.internal.provider.ConfigurationTimeBarrier
import org.gradle.api.internal.provider.DefaultConfigurationTimeBarrier
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logging
import org.gradle.configurationcache.ConfigurationCacheRepository.CheckedFingerprint
import org.gradle.configurationcache.extensions.uncheckedCast
import org.gradle.configurationcache.fingerprint.ConfigurationCacheFingerprint
import org.gradle.configurationcache.fingerprint.ConfigurationCacheFingerprintController
import org.gradle.configurationcache.fingerprint.InvalidationReason
import org.gradle.configurationcache.initialization.ConfigurationCacheStartParameter
import org.gradle.configurationcache.problems.ConfigurationCacheProblems
import org.gradle.configurationcache.serialization.DefaultWriteContext
import org.gradle.configurationcache.serialization.IsolateOwner
import org.gradle.configurationcache.serialization.withIsolate
import org.gradle.initialization.GradlePropertiesController
import org.gradle.internal.Factory
import org.gradle.internal.buildtree.BuildActionModelRequirements
import org.gradle.internal.classpath.Instrumented
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.vfs.FileSystemAccess
import org.gradle.internal.watch.vfs.BuildLifecycleAwareVirtualFileSystem
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream


class DefaultConfigurationCache internal constructor(
    private val startParameter: ConfigurationCacheStartParameter,
    private val cacheKey: ConfigurationCacheKey,
    private val problems: ConfigurationCacheProblems,
    private val scopeRegistryListener: ConfigurationCacheClassLoaderScopeRegistryListener,
    private val configurationTimeBarrier: ConfigurationTimeBarrier,
    private val buildActionModelRequirements: BuildActionModelRequirements,
    private val projectStateRegistry: ProjectStateRegistry,
    private val virtualFileSystem: BuildLifecycleAwareVirtualFileSystem,
    private val buildOperationExecutor: BuildOperationExecutor,
    /**
     * Force the [FileSystemAccess] service to be initialized as it initializes important static state.
     */
    @Suppress("unused")
    private val fileSystemAccess: FileSystemAccess
) : BuildTreeConfigurationCache, Stoppable {

    interface Host {

        val currentBuild: VintageGradleBuild

        fun createBuild(settingsFile: File?, rootProjectName: String): ConfigurationCacheBuild

        fun <T> service(serviceType: Class<T>): T

        fun <T> factory(serviceType: Class<T>): Factory<T>
    }

    private
    val canLoad by lazy { canLoad() }

    private
    var rootBuild: Host? = null

    private
    val host: Host
        get() = rootBuild!!

    private
    val cacheRepository: ConfigurationCacheRepository
        get() = host.service()

    private
    val cacheIO: ConfigurationCacheIO
        get() = host.service()

    private
    val gradlePropertiesController: GradlePropertiesController
        get() = host.service()

    private
    val cacheFingerprintController: ConfigurationCacheFingerprintController
        get() = host.service()

    private
    val systemPropertyListener: SystemPropertyAccessListener
        get() = host.service()

    override val isLoaded: Boolean
        get() = canLoad

    override fun attachRootBuild(host: Host) {
        require(rootBuild == null)
        rootBuild = host
    }

    override fun loadOrScheduleRequestedTasks(scheduler: () -> Unit) {
        if (canLoad) {
            loadWorkGraph()
        } else {
            runWorkThatContributesToCacheEntry {
                scheduler()
                saveWorkGraph()
            }
        }
    }

    override fun maybePrepareModel(action: () -> Unit) {
        if (canLoad) {
            return
        }
        runWorkThatContributesToCacheEntry {
            action()
        }
    }

    override fun <T : Any> loadOrCreateModel(creator: () -> T): T {
        if (canLoad) {
            return loadModel().uncheckedCast()
        }
        return runWorkThatContributesToCacheEntry {
            val model = creator()
            saveModel(model)
            model
        }
    }

    private
    fun canLoad(): Boolean = when {
        !startParameter.isEnabled -> {
            false
        }
        startParameter.recreateCache -> {
            logBootstrapSummary("Recreating configuration cache")
            false
        }
        startParameter.isRefreshDependencies -> {
            logBootstrapSummary(
                "{} as configuration cache cannot be reused due to {}",
                buildActionModelRequirements.actionDisplayName.capitalizedDisplayName,
                "--refresh-dependencies"
            )
            false
        }
        startParameter.isWriteDependencyLocks -> {
            logBootstrapSummary(
                "{} as configuration cache cannot be reused due to {}",
                buildActionModelRequirements.actionDisplayName.capitalizedDisplayName,
                "--write-locks"
            )
            false
        }
        startParameter.isUpdateDependencyLocks -> {
            logBootstrapSummary(
                "{} as configuration cache cannot be reused due to {}",
                buildActionModelRequirements.actionDisplayName.capitalizedDisplayName,
                "--update-locks"
            )
            false
        }
        else -> {
            when (val checkedFingerprint = checkFingerprint()) {
                is CheckedFingerprint.NotFound -> {
                    logBootstrapSummary(
                        "{} as no configuration cache is available for {}",
                        buildActionModelRequirements.actionDisplayName.capitalizedDisplayName,
                        buildActionModelRequirements.configurationCacheKeyDisplayName.displayName
                    )
                    false
                }
                is CheckedFingerprint.Invalid -> {
                    logBootstrapSummary(
                        "{} as configuration cache cannot be reused because {}.",
                        buildActionModelRequirements.actionDisplayName.capitalizedDisplayName,
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

    override fun stop() {
        Instrumented.discardListener()
    }

    private
    fun checkFingerprint(): CheckedFingerprint {
        return cacheRepository.useForFingerprintCheck(
            cacheKey.string,
            this::checkFingerprint
        )
    }

    private
    fun <T> runWorkThatContributesToCacheEntry(action: () -> T): T {
        prepareForWork()
        val result = action()
        finishWork()
        return result
    }

    private
    fun prepareForWork() {
        prepareConfigurationTimeBarrier()
        startCollectingCacheFingerprint()
        Instrumented.setListener(systemPropertyListener)
    }

    private
    fun finishWork() {
    }

    private
    fun saveModel(model: Any) {
        saveToCache(StateType.Model) { layout ->
            cacheIO.writeModelTo(model, layout.state)
            // TODO - separate out writing the metadata about included builds from writing the value
            emptySet()
        }
    }

    private
    fun saveWorkGraph() {
        saveToCache(StateType.Work) { layout -> writeConfigurationCacheState(layout) }
    }

    private
    fun saveToCache(stateType: StateType, action: (ConfigurationCacheRepository.Layout) -> Set<File>) {
        crossConfigurationTimeBarrier()

        // TODO - fingerprint should be collected until the state file has been written, as user code can run during this process
        // Moving this is currently broken because the Jar task queries provider values when serializing the manifest file tree and this
        // can cause the provider value to incorrectly be treated as a task graph input
        Instrumented.discardListener()
        stopCollectingCacheFingerprint()

        buildOperationExecutor.withStoreOperation {
            cacheRepository.useForStore(cacheKey.string, stateType) { layout ->
                problems.storing {
                    invalidateConfigurationCacheState(layout)
                }
                try {
                    val includedBuildRootDirs = action(layout)
                    writeConfigurationCacheFingerprint(
                        layout.fingerprint,
                        ConfigurationCacheFingerprint.Header(includedBuildRootDirs)
                    )
                } catch (error: ConfigurationCacheError) {
                    // Invalidate state on serialization errors
                    invalidateConfigurationCacheState(layout)
                    problems.failingBuildDueToSerializationError()
                    throw error
                } finally {
                    cacheFingerprintController.stop()
                    scopeRegistryListener.dispose()
                }
            }
        }
    }

    private
    fun loadModel(): Any {
        return loadFromCache(StateType.Model) { stateFile ->
            cacheIO.readModelFrom(stateFile)
        }
    }

    private
    fun loadWorkGraph() {
        loadFromCache(StateType.Work) { stateFile ->
            cacheIO.readRootBuildStateFrom(stateFile)
        }
    }

    private
    fun <T> loadFromCache(stateType: StateType, action: (ConfigurationCacheStateFile) -> T): T {
        prepareConfigurationTimeBarrier()
        problems.loading()

        // No need to record the `ClassLoaderScope` tree
        // when loading the task graph.
        scopeRegistryListener.dispose()

        val result = buildOperationExecutor.withLoadOperation {
            cacheRepository.useForStateLoad(cacheKey.string, stateType, action)
        }
        crossConfigurationTimeBarrier()
        return result
    }

    private
    fun prepareConfigurationTimeBarrier() {
        require(configurationTimeBarrier is DefaultConfigurationTimeBarrier)
        configurationTimeBarrier.prepare()
    }

    private
    fun crossConfigurationTimeBarrier() {
        require(configurationTimeBarrier is DefaultConfigurationTimeBarrier)
        configurationTimeBarrier.cross()
    }

    private
    fun writeConfigurationCacheState(layout: ConfigurationCacheRepository.Layout): Set<File> =
        projectStateRegistry.withMutableStateOfAllProjects(
            Factory { cacheIO.writeRootBuildStateTo(layout.state) }
        )

    private
    fun writeConfigurationCacheFingerprint(fingerprintFile: File, header: ConfigurationCacheFingerprint.Header) {
        fingerprintFile.outputStream().use { outputStream ->
            writeConfigurationCacheFingerprintHeaderTo(outputStream, header)
            cacheFingerprintController.commitFingerprintTo(outputStream)
        }
    }

    private
    fun startCollectingCacheFingerprint() {
        cacheFingerprintController.maybeStartCollectingFingerprint {
            cacheFingerprintWriterContextFor(it)
        }
    }

    private
    fun stopCollectingCacheFingerprint() {
        cacheFingerprintController.stopCollectingFingerprint()
    }

    private
    fun cacheFingerprintWriterContextFor(outputStream: OutputStream): DefaultWriteContext {
        val (context, codecs) = cacheIO.writerContextFor(outputStream, "fingerprint")
        return context.apply {
            push(IsolateOwner.OwnerHost(host), codecs.userTypesCodec)
        }
    }

    private
    fun checkFingerprint(fingerprintFile: File): InvalidationReason? {
        loadGradleProperties()
        return checkConfigurationCacheFingerprintFile(fingerprintFile)
    }

    private
    fun checkConfigurationCacheFingerprintFile(fingerprintFile: File): InvalidationReason? =
        fingerprintFile.inputStream().use { fingerprintInputStream ->
            // Register all included build root directories as watchable hierarchies
            // so we can load the fingerprint for build scripts and other files from included builds
            // without violating file system invariants.
            readConfigurationCacheFingerprintHeaderFrom(fingerprintInputStream)?.run {
                registerWatchableBuildDirectories(includedBuildRootDirs)
            }
            checkFingerprint(fingerprintInputStream)
        }

    private
    fun checkFingerprint(inputStream: FileInputStream): InvalidationReason? =
        cacheIO.withReadContextFor(inputStream) { codecs ->
            withIsolate(IsolateOwner.OwnerHost(host), codecs.userTypesCodec) {
                cacheFingerprintController.run {
                    checkFingerprint()
                }
            }
        }

    private
    fun registerWatchableBuildDirectories(buildDirs: Set<File>) {
        if (buildDirs.isNotEmpty()) {
            buildDirs.forEach(virtualFileSystem::registerWatchableHierarchy)
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
    fun logBootstrapSummary(message: String, vararg args: Any?) {
        log(message, *args)
    }

    private
    fun log(message: String, vararg args: Any?) {
        logger.log(configurationCacheLogLevel, message, *args)
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


internal
val logger = Logging.getLogger(DefaultConfigurationCache::class.java)
