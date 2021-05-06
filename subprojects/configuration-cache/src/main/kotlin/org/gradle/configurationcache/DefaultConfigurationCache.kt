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
import org.gradle.configurationcache.fingerprint.ConfigurationCacheFingerprint
import org.gradle.configurationcache.fingerprint.ConfigurationCacheFingerprintController
import org.gradle.configurationcache.fingerprint.InvalidationReason
import org.gradle.configurationcache.initialization.ConfigurationCacheBuildEnablement
import org.gradle.configurationcache.initialization.ConfigurationCacheStartParameter
import org.gradle.configurationcache.problems.ConfigurationCacheProblems
import org.gradle.configurationcache.serialization.DefaultWriteContext
import org.gradle.configurationcache.serialization.IsolateOwner
import org.gradle.configurationcache.serialization.withIsolate
import org.gradle.initialization.ConfigurationCache
import org.gradle.initialization.GradlePropertiesController
import org.gradle.internal.Factory
import org.gradle.internal.classpath.Instrumented
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.vfs.FileSystemAccess
import org.gradle.internal.watch.vfs.BuildLifecycleAwareVirtualFileSystem
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream


class DefaultConfigurationCache internal constructor(
    private val host: Host,
    private val startParameter: ConfigurationCacheStartParameter,
    private val buildEnablement: ConfigurationCacheBuildEnablement,
    private val cacheKey: ConfigurationCacheKey,
    private val problems: ConfigurationCacheProblems,
    private val systemPropertyListener: SystemPropertyAccessListener,
    private val scopeRegistryListener: ConfigurationCacheClassLoaderScopeRegistryListener,
    private val cacheIO: ConfigurationCacheIO,
    private val gradlePropertiesController: GradlePropertiesController,
    private val configurationTimeBarrier: ConfigurationTimeBarrier,
    /**
     * Force the [FileSystemAccess] service to be initialized as it initializes important static state.
     */
    @Suppress("unused")
    private val fileSystemAccess: FileSystemAccess
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
            when (val checkedFingerprint = checkFingerprint()) {
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

    private
    fun checkFingerprint(): CheckedFingerprint {
        return cacheRepository.useForFingerprintCheck(
            cacheKey.string,
            this::checkFingerprint
        )
    }

    override fun prepareForConfiguration() {

        if (!isConfigurationCacheEnabled) return

        prepareConfigurationTimeBarrier()
        startCollectingCacheFingerprint()
        Instrumented.setListener(systemPropertyListener)
    }

    override fun save() {

        if (!isConfigurationCacheEnabled) return

        crossConfigurationTimeBarrier()

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
                    scopeRegistryListener.dispose()
                }
            }
        }
    }

    override fun load() {

        require(isConfigurationCacheEnabled)

        prepareConfigurationTimeBarrier()
        problems.loading()

        // No need to record the `ClassLoaderScope` tree
        // when loading the task graph.
        scopeRegistryListener.dispose()

        buildOperationExecutor.withLoadOperation {
            cacheRepository.useForStateLoad(cacheKey.string) { stateFile ->
                cacheIO.readRootBuildStateFrom(stateFile)
            }
        }
        crossConfigurationTimeBarrier()
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
    fun writeConfigurationCacheFiles(layout: ConfigurationCacheRepository.Layout) {
        val includedBuildRootDirs = writeConfigurationCacheState(layout.state)
        writeConfigurationCacheFingerprint(
            layout.fingerprint,
            ConfigurationCacheFingerprint.Header(includedBuildRootDirs)
        )
    }

    private
    fun writeConfigurationCacheState(stateFile: ConfigurationCacheStateFile): Set<File> =
        service<ProjectStateRegistry>().withMutableStateOfAllProjects(
            Factory { cacheIO.writeRootBuildStateTo(stateFile) }
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
        cacheFingerprintController.startCollectingFingerprint {
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
    val cacheFingerprintController: ConfigurationCacheFingerprintController by lazy {
        service()
    }

    private
    val cacheRepository: ConfigurationCacheRepository by lazy {
        service()
    }

    private
    fun registerWatchableBuildDirectories(buildDirs: Set<File>) {
        if (buildDirs.isNotEmpty()) {
            buildDirs.forEach(
                service<BuildLifecycleAwareVirtualFileSystem>()::registerWatchableHierarchy
            )
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
    val buildOperationExecutor: BuildOperationExecutor
        get() = service()

    private
    inline fun <reified T> service() =
        host.service<T>()

    private
    val isConfigurationCacheEnabled: Boolean
        get() = buildEnablement.isEnabledForCurrentBuild

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
