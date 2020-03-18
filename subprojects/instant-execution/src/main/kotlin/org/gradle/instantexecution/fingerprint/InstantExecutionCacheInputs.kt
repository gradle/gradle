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
import org.gradle.instantexecution.fingerprint.InstantExecutionCacheFingerprint.TaskInputDir
import org.gradle.instantexecution.fingerprint.InstantExecutionCacheFingerprint.ValueSource
import org.gradle.instantexecution.serialization.DefaultWriteContext
import org.gradle.instantexecution.serialization.runWriteOperation
import org.gradle.internal.fingerprint.FileCollectionSnapshotter
import org.gradle.internal.hash.HashCode
import org.gradle.internal.snapshot.CompleteDirectorySnapshot
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor
import org.gradle.internal.vfs.VirtualFileSystem
import org.gradle.kotlin.dsl.support.serviceOf
import java.io.ByteArrayOutputStream
import java.io.File


internal
typealias ObtainedValue = ValueSourceProviderFactory.Listener.ObtainedValue<Any, ValueSourceParameters>


internal
sealed class InstantExecutionCacheFingerprint {

    internal
    data class TaskInputDir(
        val taskPath: String,
        val directory: File,
        val hashCode: HashCode
    ) : InstantExecutionCacheFingerprint()

    internal
    data class InputFile(
        val file: File,
        val hashCode: HashCode?
    ) : InstantExecutionCacheFingerprint()

    internal
    data class ValueSource(
        val obtainedValue: ObtainedValue
    ) : InstantExecutionCacheFingerprint()
}


internal
class InstantExecutionCacheInputs(
    private val virtualFileSystem: VirtualFileSystem,
    private val writeContext: DefaultWriteContext,
    val outputStream: ByteArrayOutputStream
) : ValueSourceProviderFactory.Listener, TaskInputsListener {

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
                            virtualFileSystem.hashCodeForFile(file)
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
            collectFileSystemInputsOf(task, fileSystemInputs)
        }
    }

    private
    fun collectFileSystemInputsOf(task: TaskInternal, fileSystemInputs: FileCollectionInternal) {
        fileCollectionSnapshotterFor(task).snapshot(fileSystemInputs).forEach { snapshot ->
            snapshot.accept(
                object : FileSystemSnapshotVisitor {
                    override fun preVisitDirectory(directorySnapshot: CompleteDirectorySnapshot): Boolean = directorySnapshot.run {
                        write(
                            TaskInputDir(
                                taskPath = task.identityPath.path,
                                directory = File(absolutePath),
                                hashCode = hash
                            )
                        )
                        false
                    }

                    override fun visitFile(fileSnapshot: CompleteFileSystemLocationSnapshot) = Unit

                    override fun postVisitDirectory(directorySnapshot: CompleteDirectorySnapshot) = Unit
                }
            )
        }
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

    private
    fun fileCollectionSnapshotterFor(task: TaskInternal) =
        task.project.serviceOf<FileCollectionSnapshotter>()
}


internal
fun VirtualFileSystem.hashCodeForFile(file: File): HashCode? =
    readRegularFileContentHash(file.path) { hashCode -> hashCode }
        .orElse(null)
