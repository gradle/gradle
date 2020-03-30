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

import org.gradle.api.execution.internal.TaskInputsListener
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.provider.ValueSourceProviderFactory
import org.gradle.api.internal.provider.sources.FileContentValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.initialization.DefaultSettingsLoader.BUILD_SRC_PROJECT_PATH
import org.gradle.instantexecution.extensions.uncheckedCast
import org.gradle.instantexecution.fingerprint.InstantExecutionCacheFingerprint.InputFile
import org.gradle.instantexecution.fingerprint.InstantExecutionCacheFingerprint.ValueSource
import org.gradle.instantexecution.serialization.DefaultWriteContext
import org.gradle.instantexecution.serialization.runWriteOperation
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.hash.HashCode
import java.io.File


internal
class InstantExecutionCacheFingerprintWriter(
    private val host: Host,
    private val writeContext: DefaultWriteContext
) : ValueSourceProviderFactory.Listener, TaskInputsListener {

    interface Host {

        fun hashCodeOf(file: File): HashCode?

        fun fingerprintOf(
            fileCollection: FileCollectionInternal,
            owner: TaskInternal
        ): CurrentFileCollectionFingerprint
    }

    /**
     * Finishes writing to the given [writeContext] and closes it.
     *
     * **MUST ALWAYS BE CALLED**
     */
    fun close() {
        write(null)
        writeContext.close()
    }

    override fun <T : Any, P : ValueSourceParameters> valueObtained(
        obtainedValue: ValueSourceProviderFactory.Listener.ObtainedValue<T, P>
    ) {
        when (val parameters = obtainedValue.valueSourceParameters) {
            is FileContentValueSource.Parameters -> {
                parameters.file.orNull?.asFile?.let { file ->
                    // TODO - consider the potential race condition in computing the hash code here
                    write(
                        InputFile(
                            file,
                            host.hashCodeOf(file)
                        )
                    )
                }
            }
            else -> {
                write(
                    ValueSource(
                        obtainedValue.uncheckedCast()
                    )
                )
            }
        }
    }

    override fun onExecute(task: TaskInternal, fileSystemInputs: FileCollectionInternal) {
        if (isBuildSrcTask(task)) {
            captureTaskInputs(task, fileSystemInputs)
        }
    }

    private
    fun captureTaskInputs(task: TaskInternal, fileSystemInputs: FileCollectionInternal) {
        write(
            InstantExecutionCacheFingerprint.TaskInputs(
                task.identityPath.path,
                fileSystemInputs,
                host.fingerprintOf(fileSystemInputs, task)
            )
        )
    }

    private
    fun write(value: InstantExecutionCacheFingerprint?) {
        synchronized(writeContext) {
            writeContext.runWriteOperation {
                write(value)
            }
        }
    }

    private
    fun isBuildSrcTask(task: TaskInternal) =
        task.taskIdentity.buildPath.path == BUILD_SRC_PROJECT_PATH
}
