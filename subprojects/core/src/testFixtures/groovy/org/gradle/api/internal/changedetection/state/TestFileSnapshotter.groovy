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

package org.gradle.api.internal.changedetection.state

import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.internal.file.collections.DirectoryFileTree
import org.gradle.internal.hash.Hashing

class TestFileSnapshotter implements FileSystemSnapshotter {
    @Override
    boolean exists(File file) {
        return file.exists()
    }

    @Override
    FileSnapshot snapshotSelf(File file) {
        if (file.isFile()) {
            return new RegularFileSnapshot(null, null, false, new FileHashSnapshot(Hashing.sha1().hashBytes(file.bytes)))
        }
        return new MissingFileSnapshot(null, null)
    }

    @Override
    Snapshot snapshotAll(File file) {
        throw new UnsupportedOperationException()
    }

    @Override
    FileTreeSnapshot snapshotDirectoryTree(File dir) {
        throw new UnsupportedOperationException()
    }

    @Override
    FileTreeSnapshot snapshotDirectoryTree(DirectoryFileTree dirTree) {
        throw new UnsupportedOperationException()
    }

    @Override
    List<FileSnapshot> snapshotTree(FileTreeInternal tree) {
        throw new UnsupportedOperationException()
    }
}
