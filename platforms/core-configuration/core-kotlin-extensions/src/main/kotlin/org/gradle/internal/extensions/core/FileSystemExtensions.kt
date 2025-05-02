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

package org.gradle.internal.extensions.core

import org.gradle.internal.file.FileType
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import java.io.File
import java.util.Arrays


fun fileSystemEntryType(file: File): FileType =
    when {
        !file.exists() -> FileType.Missing
        file.isDirectory -> FileType.Directory
        else -> FileType.RegularFile
    }


// This value is returned from directoryChildrenNamesHash when its argument doesn't exist or is not a directory.
private
val NON_DIRECTORY_CHILDREN_NAMES_HASH = HashCode.fromBytes(byteArrayOf(0, 0, 0, 0))


fun directoryChildrenNamesHash(file: File): HashCode {
    return file.list()?.let { entries ->
        val hasher = Hashing.newHasher()
        // This routine is used to snapshot results of File.list(), File.listFiles() and similar calls.
        // Technically, the relative order or entries is visible to the caller, so it might be considered when computing the hash.
        // However, the File.list spec leaves the order undefined, so no reasonable implementation should depend on it.
        // Making the hash order-independent by sorting the entries maximizes CC hits, even if the OS-returned order changes.
        Arrays.sort(entries)
        entries.forEach(hasher::putString)
        hasher.hash()
    } ?: NON_DIRECTORY_CHILDREN_NAMES_HASH
}
