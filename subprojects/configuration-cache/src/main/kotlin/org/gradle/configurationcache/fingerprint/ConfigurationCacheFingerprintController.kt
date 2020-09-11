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
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.api.internal.provider.DefaultValueSourceProviderFactory
import org.gradle.api.internal.provider.ValueSourceProviderFactory
import org.gradle.configurationcache.BuildTreeListenerManager
import org.gradle.configurationcache.extensions.hashCodeOf
import org.gradle.configurationcache.extensions.serviceOf
import org.gradle.configurationcache.initialization.ConfigurationCacheStartParameter
import org.gradle.configurationcache.serialization.DefaultWriteContext
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.fingerprint.impl.AbsolutePathFileCollectionFingerprinter
import org.gradle.internal.hash.HashCode
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.internal.vfs.FileSystemAccess
import org.gradle.util.BuildCommencedTimeProvider
import org.gradle.util.GFileUtils
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream


/**
 * Coordinates the writing and reading of the configuration cache fingerprint.
 */
@ServiceScope(Scopes.Build::class)
internal
class ConfigurationCacheFingerprintController internal constructor(
    private val startParameter: ConfigurationCacheStartParameter,
    private val taskInputsListeners: TaskInputsListeners,
    private val valueSourceProviderFactory: ValueSourceProviderFactory,
    private val fileSystemAccess: FileSystemAccess,
    private val fileCollectionFingerprinter: AbsolutePathFileCollectionFingerprinter,
    private val buildCommencedTimeProvider: BuildCommencedTimeProvider,
    private val listenerManager: ListenerManager,
    private val buildTreeListenerManager: BuildTreeListenerManager,
    private val fileCollectionFactory: FileCollectionFactory,
    private val directoryFileTreeFactory: DirectoryFileTreeFactory
) : Stoppable {

    private
    abstract class WritingState {

        open fun start(writeContextForOutputStream: (OutputStream) -> DefaultWriteContext): WritingState =
            illegalStateFor("start")

        open fun stop(): WritingState =
            illegalStateFor("stop")

        open fun commit(fingerprintFile: File): WritingState =
            illegalStateFor("commit")

        abstract fun dispose(): WritingState

        private
        fun illegalStateFor(operation: String): Nothing = throw IllegalStateException(
            "'$operation' is illegal while in '${javaClass.simpleName}' state."
        )
    }

    private
    inner class Idle : WritingState() {
        override fun start(writeContextForOutputStream: (OutputStream) -> DefaultWriteContext): WritingState {
            val outputStream = ByteArrayOutputStream()
            val fingerprintWriter = ConfigurationCacheFingerprintWriter(
                CacheFingerprintComponentHost(),
                writeContextForOutputStream(outputStream),
                fileCollectionFactory,
                directoryFileTreeFactory
            )
            addListener(fingerprintWriter)
            return Writing(fingerprintWriter, outputStream)
        }

        override fun dispose(): WritingState =
            this
    }

    private
    inner class Writing(
        private val fingerprintWriter: ConfigurationCacheFingerprintWriter,
        private val outputStream: ByteArrayOutputStream
    ) : WritingState() {
        override fun stop(): WritingState {
            // TODO - this is a temporary step, see the comment in DefaultConfigurationCache
            fingerprintWriter.stopCollectingValueSources()
            return Written(fingerprintWriter, outputStream)
        }

        override fun dispose() =
            stop().dispose()
    }

    private
    inner class Written(
        private val fingerprintWriter: ConfigurationCacheFingerprintWriter,
        private val outputStream: ByteArrayOutputStream
    ) : WritingState() {
        override fun commit(fingerprintFile: File): WritingState {
            dispose()
            fingerprintFile
                .outputStream()
                .use(outputStream::writeTo)
            return Idle()
        }

        override fun dispose(): WritingState {
            removeListener(fingerprintWriter)
            fingerprintWriter.close()
            return Idle()
        }
    }

    private
    var writingState: WritingState = Idle()

    fun startCollectingFingerprint(writeContextForOutputStream: (OutputStream) -> DefaultWriteContext) {
        writingState = writingState.start(writeContextForOutputStream)
    }

    fun stopCollectingFingerprint() {
        writingState = writingState.stop()
    }

    fun commitFingerprintTo(fingerprintFile: File) {
        writingState = writingState.commit(fingerprintFile)
    }

    override fun stop() {
        writingState = writingState.dispose()
    }

    suspend fun ReadContext.checkFingerprint(): InvalidationReason? =
        ConfigurationCacheFingerprintChecker(CacheFingerprintComponentHost()).run {
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
    inner class CacheFingerprintComponentHost :
        ConfigurationCacheFingerprintWriter.Host, ConfigurationCacheFingerprintChecker.Host {

        override val gradleUserHomeDir: File
            get() = startParameter.gradleUserHomeDir

        override val allInitScripts: List<File>
            get() = startParameter.allInitScripts

        override val buildStartTime: Long
            get() = buildCommencedTimeProvider.currentTime

        override fun hashCodeOf(file: File) =
            fileSystemAccess.hashCodeOf(file)

        override fun fingerprintOf(
            fileCollection: FileCollectionInternal,
            owner: TaskInternal
        ): HashCode =
            fileCollectionFingerprinterFor(owner).fingerprint(fileCollection).hash

        override fun fingerprintOf(
            fileCollection: FileCollectionInternal
        ): HashCode =
            fileCollectionFingerprinter.fingerprint(fileCollection).hash

        override fun displayNameOf(fileOrDirectory: File): String =
            GFileUtils.relativePathOf(fileOrDirectory, rootDirectory)

        override fun instantiateValueSourceOf(obtainedValue: ObtainedValue) =
            (valueSourceProviderFactory as DefaultValueSourceProviderFactory).instantiateValueSource(
                obtainedValue.valueSourceType,
                obtainedValue.valueSourceParametersType,
                obtainedValue.valueSourceParameters
            )

        private
        fun fileCollectionFingerprinterFor(task: TaskInternal) =
            task.serviceOf<AbsolutePathFileCollectionFingerprinter>()

        private
        val rootDirectory
            get() = startParameter.rootDirectory
    }
}
