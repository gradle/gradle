/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.hash;

import java.io.File;

public interface FileInfoCollector extends FileHasher {
    /**
     * Returns the {@link FileInfo} of the current content of the given file, assuming the given file metadata. The provided file must exist and be a file (rather than, say, a directory).
     */
    FileInfo collect(File file, long length, long lastModified);

    /**
     * Adapts a {@link FileHasher} to a {@link FileInfoCollector} object, returning a {@link FileInfo} object
     * with an UNKNOWN file content type.
     */
    class Adapter implements FileInfoCollector {
        private final FileHasher delegate;

        public Adapter(FileHasher delegate) {
            this.delegate = delegate;
        }

        @Override
        public HashCode hash(File file) {
            return delegate.hash(file);
        }

        @Override
        public HashCode hash(File file, long length, long lastModified) {
            return delegate.hash(file, length, lastModified);
        }

        @Override
        public FileInfo collect(File file, long length, long lastModified) {
            return new FileInfo(delegate.hash(file, length, lastModified), length, lastModified, FileContentType.UNKNOWN);
        }

        static FileInfoCollector from(FileHasher fileHasher) {
            return new Adapter(fileHasher);
        }
    }
}
