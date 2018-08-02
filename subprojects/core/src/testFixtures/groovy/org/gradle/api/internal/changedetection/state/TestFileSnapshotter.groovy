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

import org.gradle.api.internal.changedetection.state.mirror.FileSystemSnapshot
import org.gradle.api.internal.changedetection.state.mirror.PhysicalFileSnapshot
import org.gradle.api.internal.changedetection.state.mirror.PhysicalMissingSnapshot
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshot
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing

class TestFileSnapshotter implements FileSystemSnapshotter {
    @Override
    boolean exists(File file) {
        return file.exists()
    }

    @Override
    HashCode getRegularFileContentHash(File file) {
        return file.isFile() ? Hashing.md5().hashBytes(file.bytes) : null
    }

    @Override
    PhysicalSnapshot snapshot(File file) {
        if (file.isFile()) {
            return new PhysicalFileSnapshot(file.absolutePath, file.name, Hashing.md5().hashBytes(file.bytes), file.lastModified())
        }
        if (!file.exists()) {
            return new PhysicalMissingSnapshot(file.absolutePath, file.name)
        }
        throw new UnsupportedOperationException()
    }

    @Override
    List<FileSystemSnapshot> snapshot(FileCollectionInternal fileCollection) {
        throw new UnsupportedOperationException()
    }
}
