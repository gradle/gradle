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

package org.gradle.internal.hash

import org.gradle.api.file.FileTreeElement
import org.gradle.internal.file.FileMetadataSnapshot

class TestFileHasher implements FileHasher {
    private final static HASH_FUNCTION = Hashing.md5()

    @Override
    HashCode hash(InputStream inputStream) {
        return HASH_FUNCTION.hashBytes(inputStream.bytes)
    }

    @Override
    HashCode hashCopy(InputStream inputStream, OutputStream outputStream) throws IOException {
        def hashOutputStream = new HashingOutputStream(HASH_FUNCTION, outputStream)
        hashOutputStream << inputStream
        return hashOutputStream.hash()
    }

    @Override
    HashCode hash(File file) {
        HASH_FUNCTION.hashBytes(file.bytes)
    }

    @Override
    HashCode hash(FileTreeElement fileDetails) {
        return hash(fileDetails.file)
    }

    @Override
    HashCode hash(File file, FileMetadataSnapshot fileDetails) {
        return hash(file)
    }
}
