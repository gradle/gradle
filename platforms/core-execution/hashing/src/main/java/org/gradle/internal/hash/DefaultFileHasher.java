/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.UncheckedIOException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class DefaultFileHasher implements FileHasher {
    private final StreamHasher streamHasher;

    public DefaultFileHasher(StreamHasher streamHasher) {
        this.streamHasher = streamHasher;
    }

    @Override
    public HashCode hash(File file) {
        InputStream inputStream;
        try {
            inputStream = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            throw new UncheckedIOException(String.format("Failed to create MD5 hash for file '%s' as it does not exist.", file), e);
        }
        try {
            return streamHasher.hash(inputStream);
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("Failed to create MD5 hash for file '%s'", file), e);
        } finally {
            try {
                inputStream.close();
            } catch (IOException ignored) {
                // Ignored
            }
        }
    }

    @Override
    public HashCode hash(File file, long length, long lastModified) {
        return hash(file);
    }
}
