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
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.provider.DefaultValueSourceProviderFactory
import org.gradle.api.internal.provider.ValueSourceProviderFactory
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


internal
class InstantExecutionCacheFingerprintController internal constructor(
    private val startParameter: InstantExecutionStartParameter,
    private val taskInputsListeners: TaskInputsListeners,
    private val valueSourceProviderFactory: ValueSourceProviderFactory,
    private val virtualFileSystem: VirtualFileSystem,
    private val fileCollectionFingerprinter: AbsolutePathFileCollectionFingerprinter
) {

    private
    var fingerprintWriter: InstantExecutionCacheFingerprintWriter? = null

    private
    var fingerprintOutputStream: ByteArrayOutputStream? = null

    fun start(writeContextForOutputStream: (OutputStream) -> DefaultWriteContext) {
        val outputStream = ByteArrayOutputStream()
        InstantExecutionCacheFingerprintWriter(
            virtualFileSystem,
            writeContextForOutputStream(outputStream)
        ).also { writer ->
            fingerprintWriter = writer
            fingerprintOutputStream = outputStream
            valueSourceProviderFactory.addListener(writer)
            taskInputsListeners.addListener(writer)
        }
    }

    fun stop() {
        fingerprintWriter.let { writer ->
            require(writer != null)
            taskInputsListeners.removeListener(writer)
            valueSourceProviderFactory.removeListener(writer)
            writer.close()
        }
        fingerprintWriter = null
    }

    fun writeFingerprintTo(fingerprintFile: File) {
        fingerprintOutputStream.let { outputStream ->
            require(outputStream != null)
            fingerprintFile
                .outputStream()
                .use(outputStream::writeTo)
        }
        fingerprintOutputStream = null
    }

    suspend fun ReadContext.check(): InvalidationReason? =
        fingerprintChecker().run {
            checkFingerprint()
        }

    private
    fun fingerprintChecker() =
        InstantExecutionFingerprintChecker(InstantExecutionFingerprintCheckerHost())

    private
    inner class InstantExecutionFingerprintCheckerHost : InstantExecutionFingerprintChecker.Host {

        override fun hashCodeOf(file: File) =
            virtualFileSystem.hashCodeOf(file)

        override fun fingerprintOf(fileCollection: FileCollectionInternal): CurrentFileCollectionFingerprint =
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
        val rootDirectory
            get() = startParameter.rootDirectory
    }
}
