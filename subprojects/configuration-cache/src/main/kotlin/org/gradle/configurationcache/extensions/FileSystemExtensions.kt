/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.configurationcache.extensions

import org.gradle.internal.file.FileType
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import org.gradle.internal.snapshot.DirectorySnapshot
import org.gradle.internal.snapshot.SnapshotVisitResult
import org.gradle.internal.vfs.FileSystemAccess
import java.io.File


fun fileSystemEntryType(file: File): FileType =
    when {
        !file.exists() -> FileType.Missing
        file.isDirectory -> FileType.Directory
        else -> FileType.RegularFile
    }


internal
fun FileSystemAccess.directoryContentHash(file: File): HashCode =
    when (val location = read(file.path)) {
        is DirectorySnapshot -> {
            val hasher = Hashing.newHasher()
            location.accept { locationOrChild ->
                when (locationOrChild) {
                    location -> SnapshotVisitResult.CONTINUE
                    else -> {
                        hasher.putString(locationOrChild.name)
                        SnapshotVisitResult.SKIP_SUBTREE
                    }
                }
            }
            hasher.hash()
        }

        else -> HashCode.fromBytes(byteArrayOf(0, 0, 0, 0))
    }
