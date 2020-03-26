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

package org.gradle.instantexecution.fingerprint

import org.gradle.api.execution.internal.TaskInputsListeners
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.provider.DefaultValueSourceProviderFactory
import org.gradle.api.internal.provider.ValueSourceProviderFactory
import org.gradle.instantexecution.extensions.hashCodeOf
import org.gradle.instantexecution.extensions.serviceOf
import org.gradle.instantexecution.initialization.InstantExecutionStartParameter
import org.gradle.instantexecution.serialization.DefaultWriteContext
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.fingerprint.impl.AbsolutePathFileCollectionFingerprinter
import org.gradle.internal.vfs.VirtualFileSystem
import org.gradle.util.GFileUtils
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream


/**
 * Coordinates the writing and reading of the instant execution cache fingerprint.
 */
internal
class InstantExecutionCacheFingerprintController internal constructor(
    private val startParameter: InstantExecutionStartParameter,
    private val taskInputsListeners: TaskInputsListeners,
    private val valueSourceProviderFactory: ValueSourceProviderFactory,
    private val virtualFileSystem: VirtualFileSystem,
    private val fileCollectionFingerprinter: AbsolutePathFileCollectionFingerprinter
) {

    private
    open class WritingState {

        open fun start(writeContextForOutputStream: (OutputStream) -> DefaultWriteContext): WritingState =
            illegalStateFor("start")

        open fun stop(): WritingState =
            illegalStateFor("stop")

        open fun commit(fingerprintFile: File): WritingState =
            illegalStateFor("commit")

        private
        fun illegalStateFor(operation: String): Nothing = throw IllegalStateException(
            "'$operation' is illegal while in '${javaClass.simpleName}' state."
        )
    }

    private
    inner class Idle : WritingState() {
        override fun start(writeContextForOutputStream: (OutputStream) -> DefaultWriteContext): WritingState {
            val outputStream = ByteArrayOutputStream()
            val fingerprintWriter = InstantExecutionCacheFingerprintWriter(
                CacheFingerprintComponentHost(),
                writeContextForOutputStream(outputStream)
            )
            addListener(fingerprintWriter)
            return Writing(fingerprintWriter, outputStream)
        }
    }

    private
    inner class Writing(
        private val fingerprintWriter: InstantExecutionCacheFingerprintWriter,
        private val outputStream: ByteArrayOutputStream
    ) : WritingState() {
        override fun stop(): WritingState {
            removeListener(fingerprintWriter)
            fingerprintWriter.close()
            return Written(outputStream)
        }
    }

    private
    inner class Written(
        private val outputStream: ByteArrayOutputStream
    ) : WritingState() {
        override fun commit(fingerprintFile: File): WritingState {
            fingerprintFile
                .outputStream()
                .use(outputStream::writeTo)
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

    suspend fun ReadContext.checkFingerprint(): InvalidationReason? =
        InstantExecutionCacheFingerprintChecker(CacheFingerprintComponentHost()).run {
            checkFingerprint()
        }

    private
    fun addListener(listener: InstantExecutionCacheFingerprintWriter) {
        valueSourceProviderFactory.addListener(listener)
        taskInputsListeners.addListener(listener)
    }

    private
    fun removeListener(listener: InstantExecutionCacheFingerprintWriter) {
        taskInputsListeners.removeListener(listener)
        valueSourceProviderFactory.removeListener(listener)
    }

    private
    inner class CacheFingerprintComponentHost
        : InstantExecutionCacheFingerprintWriter.Host, InstantExecutionCacheFingerprintChecker.Host {

        override fun hashCodeOf(file: File) =
            virtualFileSystem.hashCodeOf(file)

        override fun fingerprintOf(
            fileCollection: FileCollectionInternal,
            owner: TaskInternal
        ): CurrentFileCollectionFingerprint =
            fileCollectionFingerprinterFor(owner).fingerprint(fileCollection)

        override fun fingerprintOf(
            fileCollection: FileCollectionInternal
        ): CurrentFileCollectionFingerprint =
            fileCollectionFingerprinter.fingerprint(fileCollection)

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
