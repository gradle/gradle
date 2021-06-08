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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;

public class DefaultFileInfoCollector implements FileInfoCollector {
    private final StreamHasher streamHasher;

    public DefaultFileInfoCollector(StreamHasher streamHasher) {
        this.streamHasher = streamHasher;
    }

    @Override
    public HashCode hash(File file) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HashCode hash(File file, long length, long lastModified) {
        return collect(file, length, lastModified).getHash();
    }

    @Override
    public FileInfo collect(File file, long length, long lastModified) {
        FileContentTypeDetectingInputStream inputStream;
        try {
            inputStream = new FileContentTypeDetectingInputStream(new FileInputStream(file));
        } catch (FileNotFoundException e) {
            throw new UncheckedIOException(String.format("Failed to create MD5 hash for file '%s' as it does not exist.", file), e);
        }
        try {
            HashCode hashCode = streamHasher.hash(inputStream);
            return new FileInfo(hashCode, length, lastModified, inputStream.getContentType());
        } finally {
            try {
                inputStream.close();
            } catch (IOException ignored) {
                // Ignored
            }
        }
    }
}
