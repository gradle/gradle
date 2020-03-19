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

import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.internal.fingerprint.FileCollectionSnapshotter
import org.gradle.internal.hash.HashCode
import org.gradle.internal.snapshot.CompleteDirectorySnapshot
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor
import java.io.File


internal
sealed class FileCollectionFingerprint {
    data class TaskInputDir(
        val directory: File,
        val hashCode: HashCode
    ) : FileCollectionFingerprint()
}


internal
fun FileCollectionSnapshotter.fingerprintFor(fileSystemInputs: FileCollectionInternal): List<FileCollectionFingerprint.TaskInputDir> {
    val fingerprint = mutableListOf<FileCollectionFingerprint.TaskInputDir>()
    snapshot(fileSystemInputs).forEach { snapshot ->
        snapshot.accept(
            object : FileSystemSnapshotVisitor {
                override fun preVisitDirectory(directorySnapshot: CompleteDirectorySnapshot): Boolean = directorySnapshot.run {
                    fingerprint.add(
                        FileCollectionFingerprint.TaskInputDir(
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
    return fingerprint
}
