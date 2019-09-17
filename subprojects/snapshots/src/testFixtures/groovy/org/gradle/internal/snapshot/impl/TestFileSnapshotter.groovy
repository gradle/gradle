/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.snapshot.impl

import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import org.gradle.internal.snapshot.FileMetadata
import org.gradle.internal.snapshot.FileSystemLocationSnapshot
import org.gradle.internal.snapshot.FileSystemSnapshot
import org.gradle.internal.snapshot.FileSystemSnapshotBuilder
import org.gradle.internal.snapshot.FileSystemSnapshotter
import org.gradle.internal.snapshot.MissingFileSnapshot
import org.gradle.internal.snapshot.RegularFileSnapshot
import org.gradle.internal.snapshot.SnapshottingFilter

import java.util.function.Consumer

class TestFileSnapshotter implements FileSystemSnapshotter {

    @Override
    HashCode getRegularFileContentHash(File file) {
        return file.isFile() ? Hashing.hashBytes(file.bytes) : null
    }

    @Override
    FileSystemLocationSnapshot snapshot(File file) {
        if (file.isFile()) {
            return new RegularFileSnapshot(file.absolutePath, file.name, Hashing.hashBytes(file.bytes), new FileMetadata(file.length(), file.lastModified()))
        }
        if (!file.exists()) {
            return new MissingFileSnapshot(file.absolutePath, file.name)
        }
        throw new UnsupportedOperationException()
    }

    @Override
    FileSystemSnapshot snapshotDirectoryTree(File root, SnapshottingFilter filter) {
        throw new UnsupportedOperationException()
    }

    @Override
    FileSystemSnapshot snapshotWithBuilder(Consumer<FileSystemSnapshotBuilder> buildAction) {
        throw new UnsupportedOperationException()
    }
}
