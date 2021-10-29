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

package org.gradle.configurationcache.fingerprint

import org.gradle.api.execution.internal.TaskInputsListeners
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.api.internal.properties.GradleProperties
import org.gradle.api.internal.provider.DefaultValueSourceProviderFactory
import org.gradle.api.internal.provider.ValueSourceProviderFactory
import org.gradle.configuration.internal.UserCodeApplicationContext
import org.gradle.configurationcache.BuildTreeListenerManager
import org.gradle.configurationcache.CheckedFingerprint
import org.gradle.configurationcache.ConfigurationCacheStateFile
import org.gradle.configurationcache.extensions.hashCodeOf
import org.gradle.configurationcache.initialization.ConfigurationCacheStartParameter
import org.gradle.configurationcache.problems.ConfigurationCacheReport
import org.gradle.configurationcache.problems.PropertyProblem
import org.gradle.configurationcache.problems.location
import org.gradle.configurationcache.serialization.DefaultWriteContext
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.execution.fingerprint.FileCollectionFingerprinterRegistry
import org.gradle.internal.execution.fingerprint.impl.DefaultFileNormalizationSpec
import org.gradle.internal.fingerprint.AbsolutePathInputNormalizer
import org.gradle.internal.fingerprint.DirectorySensitivity
import org.gradle.internal.fingerprint.LineEndingSensitivity
import org.gradle.internal.hash.HashCode
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.internal.vfs.FileSystemAccess
import org.gradle.util.Path
import org.gradle.util.internal.BuildCommencedTimeProvider
import org.gradle.util.internal.GFileUtils
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.file.Files


/**
 * Coordinates the writing and reading of the configuration cache fingerprint.
 */
@ServiceScope(Scopes.BuildTree::class)
internal
class ConfigurationCacheFingerprintController internal constructor(
    private val startParameter: ConfigurationCacheStartParameter,
    private val taskInputsListeners: TaskInputsListeners,
    private val fileSystemAccess: FileSystemAccess,
    fingerprinterRegistry: FileCollectionFingerprinterRegistry,
    private val buildCommencedTimeProvider: BuildCommencedTimeProvider,
    private val listenerManager: ListenerManager,
    private val buildTreeListenerManager: BuildTreeListenerManager,
    private val fileCollectionFactory: FileCollectionFactory,
    private val directoryFileTreeFactory: DirectoryFileTreeFactory,
    private val report: ConfigurationCacheReport,
    private val userCodeApplicationContext: UserCodeApplicationContext
) : Stoppable {

    interface Host {
        val valueSourceProviderFactory: ValueSourceProviderFactory
        val gradleProperties: GradleProperties
    }

    private
    val fileCollectionFingerprinter = fingerprinterRegistry.getFingerprinter(DefaultFileNormalizationSpec.from(AbsolutePathInputNormalizer::class.java, DirectorySensitivity.DEFAULT, LineEndingSensitivity.DEFAULT))

    private
    abstract class WritingState {

        open fun maybeStart(spoolFile: File, writeContextForOutputStream: (OutputStream) -> DefaultWriteContext): WritingState =
            illegalStateFor("start")

        open fun pause(): WritingState =
            illegalStateFor("pause")

        open fun commit(stateFile: ConfigurationCacheStateFile): WritingState =
            illegalStateFor("commit")

        open fun <T> collectFingerprintForProject(identityPath: Path, action: () -> T): T =
            illegalStateFor("collectFingerprintForProject")

        abstract fun dispose(): WritingState

        private
        fun illegalStateFor(operation: String): Nothing = throw IllegalStateException(
            "'$operation' is illegal while in '${javaClass.simpleName}' state."
        )
    }

    private
    inner class Idle : WritingState() {
        override fun maybeStart(spoolFile: File, writeContextForOutputStream: (OutputStream) -> DefaultWriteContext): WritingState {
            val outputStream = FileOutputStream(spoolFile)
            val fingerprintWriter = ConfigurationCacheFingerprintWriter(
                CacheFingerprintWriterHost(),
                writeContextForOutputStream(outputStream),
                fileCollectionFactory,
                directoryFileTreeFactory
            )
            addListener(fingerprintWriter)
            return Writing(fingerprintWriter, spoolFile, outputStream)
        }

        override fun dispose(): WritingState =
            this
    }

    private
    inner class Writing(
        private val fingerprintWriter: ConfigurationCacheFingerprintWriter,
        private val spoolFile: File,
        private val outputStream: OutputStream
    ) : WritingState() {
        override fun maybeStart(spoolFile: File, writeContextForOutputStream: (OutputStream) -> DefaultWriteContext): WritingState {
            return this
        }

        override fun <T> collectFingerprintForProject(identityPath: Path, action: () -> T): T {
            return fingerprintWriter.collectFingerprintForProject(identityPath, action)
        }

        override fun pause(): WritingState {
            removeListener(fingerprintWriter)
            return Paused(fingerprintWriter, spoolFile, outputStream)
        }

        override fun dispose() =
            pause().dispose()
    }

    private
    inner class Paused(
        private val fingerprintWriter: ConfigurationCacheFingerprintWriter,
        private val spoolFile: File,
        private val outputStream: OutputStream
    ) : WritingState() {
        override fun maybeStart(spoolFile: File, writeContextForOutputStream: (OutputStream) -> DefaultWriteContext): WritingState {
            addListener(fingerprintWriter)
            // Continue with the current spool file, rather than starting a new one
            return Writing(fingerprintWriter, this.spoolFile, outputStream)
        }

        override fun pause(): WritingState {
            return this
        }

        override fun commit(stateFile: ConfigurationCacheStateFile): WritingState {
            closeStreams()
            stateFile.moveFrom(spoolFile)
            return Committed()
        }

        override fun dispose(): WritingState {
            closeStreams()
            if (spoolFile.exists()) {
                Files.delete(spoolFile.toPath())
            }
            return Idle()
        }

        private
        fun closeStreams() {
            fingerprintWriter.close()
            outputStream.close()
        }
    }

    private
    class Committed : WritingState() {
        override fun dispose(): WritingState {
            return this
        }
    }

    private
    var writingState: WritingState = Idle()

    // Start fingerprinting if not already started and not already committed
    // This should be strict but currently this method may be called multiple times when a
    // build invocation both runs tasks and queries models
    fun maybeStartCollectingFingerprint(spoolFile: File, writeContextForOutputStream: (OutputStream) -> DefaultWriteContext) {
        writingState = writingState.maybeStart(spoolFile, writeContextForOutputStream)
    }

    fun stopCollectingFingerprint() {
        writingState = writingState.pause()
    }

    fun commitFingerprintTo(stateFile: ConfigurationCacheStateFile) {
        writingState = writingState.commit(stateFile)
    }

    /**
     * Runs the given action that is specific to the given project, and associates any build inputs read by the current thread
     * with the project.
     */
    fun <T> collectFingerprintForProject(identityPath: Path, action: () -> T): T {
        return writingState.collectFingerprintForProject(identityPath, action)
    }

    override fun stop() {
        writingState = writingState.dispose()
    }

    suspend fun ReadContext.checkFingerprint(host: Host): CheckedFingerprint =
        ConfigurationCacheFingerprintChecker(CacheFingerprintCheckerHost(host)).run {
            checkFingerprint()
        }

    private
    fun addListener(listener: ConfigurationCacheFingerprintWriter) {
        listenerManager.addListener(listener)
        buildTreeListenerManager.service.addListener(listener)
        taskInputsListeners.addListener(listener)
    }

    private
    fun removeListener(listener: ConfigurationCacheFingerprintWriter) {
        taskInputsListeners.removeListener(listener)
        buildTreeListenerManager.service.removeListener(listener)
        listenerManager.removeListener(listener)
    }

    private
    inner class CacheFingerprintWriterHost :
        ConfigurationCacheFingerprintWriter.Host {

        override val gradleUserHomeDir: File
            get() = startParameter.gradleUserHomeDir

        override val allInitScripts: List<File>
            get() = startParameter.allInitScripts

        override val buildStartTime: Long
            get() = buildCommencedTimeProvider.currentTime

        override fun hashCodeOf(file: File) =
            fileSystemAccess.hashCodeOf(file)

        override fun fingerprintOf(fileCollection: FileCollectionInternal): HashCode =
            fileCollectionFingerprinter.fingerprint(fileCollection).hash

        override fun reportInput(input: PropertyProblem) =
            report.onInput(input)

        override fun location(consumer: String?) =
            userCodeApplicationContext.location(consumer)
    }

    private
    inner class CacheFingerprintCheckerHost(
        private val host: Host
    ) : ConfigurationCacheFingerprintChecker.Host {

        private
        val gradleProperties by lazy(host::gradleProperties)

        override val gradleUserHomeDir: File
            get() = startParameter.gradleUserHomeDir

        override val allInitScripts: List<File>
            get() = startParameter.allInitScripts

        override val buildStartTime: Long
            get() = buildCommencedTimeProvider.currentTime

        override fun gradleProperty(propertyName: String): String? =
            gradleProperties.find(propertyName)

        override fun hashCodeOf(file: File) =
            fileSystemAccess.hashCodeOf(file)

        override fun fingerprintOf(fileCollection: FileCollectionInternal): HashCode =
            fileCollectionFingerprinter.fingerprint(fileCollection).hash

        override fun displayNameOf(fileOrDirectory: File): String =
            GFileUtils.relativePathOf(fileOrDirectory, rootDirectory)

        override fun instantiateValueSourceOf(obtainedValue: ObtainedValue) =
            (host.valueSourceProviderFactory as DefaultValueSourceProviderFactory).instantiateValueSource(
                obtainedValue.valueSourceType,
                obtainedValue.valueSourceParametersType,
                obtainedValue.valueSourceParameters
            )

        private
        val rootDirectory
            get() = startParameter.rootDirectory
    }
}
