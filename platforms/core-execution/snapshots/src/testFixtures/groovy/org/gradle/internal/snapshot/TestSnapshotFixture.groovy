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

package org.gradle.internal.snapshot

import org.apache.commons.io.FilenameUtils
import org.gradle.internal.file.FileMetadata
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.vfs.impl.DefaultSnapshotHierarchy

import javax.annotation.Nullable

import static java.lang.Math.abs
import static org.gradle.internal.file.FileMetadata.AccessType.DIRECT
import static org.gradle.internal.file.impl.DefaultFileMetadata.file

trait TestSnapshotFixture {

    private final Random pseudoRandom = new Random(1234)

    FileSystemLocationSnapshot directory(String absolutePath, FileMetadata.AccessType accessType = DIRECT, Long hashCode = null, List<FileSystemLocationSnapshot> children) {
        def builder = MerkleDirectorySnapshotBuilder.sortingRequired()
        builder.enterDirectory(
            accessType,
            FilenameUtils.separatorsToSystem(absolutePath),
            FilenameUtils.getName(absolutePath),
            DirectorySnapshotBuilder.EmptyDirectoryHandlingStrategy.INCLUDE_EMPTY_DIRS
        )
        children.each { snapshot ->
            if (snapshot instanceof DirectorySnapshot) {
                builder.visitDirectory(snapshot)
            } else {
                builder.visitLeafElement(snapshot)
            }
        }
        return builder.leaveDirectory()
    }

    FileSystemLocationSnapshot regularFile(String absolutePath, FileMetadata.AccessType accessType = DIRECT) {
        regularFile(absolutePath, accessType, null)
    }

    FileSystemLocationSnapshot regularFile(String absolutePath, @Nullable Long hashCode) {
        regularFile(absolutePath, DIRECT, hashCode)
    }

    FileSystemLocationSnapshot regularFile(String absolutePath, FileMetadata.AccessType accessType, @Nullable Long hashCode) {
        new RegularFileSnapshot(
            FilenameUtils.separatorsToSystem(absolutePath),
            FilenameUtils.getName(absolutePath),
            TestHashCodes.hashCodeFrom(hashCode ?: pseudoRandom.nextLong()),
            file(abs(pseudoRandom.nextLong()), abs(pseudoRandom.nextLong()), accessType))
    }

    FileSystemLocationSnapshot missing(String absolutePath, FileMetadata.AccessType accessType = DIRECT) {
        new MissingFileSnapshot(
            absolutePath,
            accessType
        )
    }

    static SnapshotHierarchy buildHierarchy(CaseSensitivity caseSensitivity = CaseSensitivity.CASE_INSENSITIVE, List<FileSystemLocationSnapshot> snapshots) {
        SnapshotHierarchy root = DefaultSnapshotHierarchy.empty(caseSensitivity)
        snapshots.each { snapshot -> root = root.store(snapshot.absolutePath, snapshot, SnapshotHierarchy.NodeDiffListener.NOOP)}
        return root
    }
}
