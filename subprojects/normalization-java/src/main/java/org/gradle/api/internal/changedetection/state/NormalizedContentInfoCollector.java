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

package org.gradle.api.internal.changedetection.state;

import org.gradle.internal.hash.FileContentType;
import org.gradle.internal.hash.FileContentTypeDetectingInputStream;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.StreamHasher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.function.Function;

/**
 * Collects content normalization information about a file including whether the file is text or binary and the normalized hash
 * produced by hashing the file using the provided input stream decorator.  New content normalizations can be produced by providing
 * a decorator that creates a new input stream that filters the provided input stream in some meaningful way.
 */
public class NormalizedContentInfoCollector {
    private final Function<InputStream, InputStream> streamDecorator;
    private final StreamHasher streamHasher;

    public NormalizedContentInfoCollector(Function<InputStream, InputStream> normalizingStreamDecorator, StreamHasher streamHasher) {
        this.streamDecorator = normalizingStreamDecorator;
        this.streamHasher = streamHasher;
    }

    public NormalizedContentInfo collect(InputStream inputStream) {
        FileContentTypeDetectingInputStream contentTypeDetectingInputStream = new FileContentTypeDetectingInputStream(inputStream);
        InputStream normalizedInputStream = streamDecorator.apply(contentTypeDetectingInputStream);

        try {
            HashCode hashCode = streamHasher.hash(normalizedInputStream);
            return new NormalizedContentInfo(hashCode, contentTypeDetectingInputStream.getContentType());
        } finally {
            try {
                contentTypeDetectingInputStream.close();
            } catch (IOException ignored) {
                // Ignored
            }

            try {
                normalizedInputStream.close();
            } catch (IOException ignored) {
                // Ignored
            }
        }
    }

    public NormalizedContentInfo collect(File file) {
        FileInputStream inputStream;
        try {
          inputStream = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            throw new UncheckedIOException(String.format("Failed to create MD5 hash for file '%s' as it does not exist.", file), e);
        }
        try {
            return collect(inputStream);
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                // Ignored
            }
        }
    }

    public static class NormalizedContentInfo {
        private final HashCode hash;
        private final FileContentType contentType;

        public NormalizedContentInfo(HashCode hash, FileContentType contentType) {
            this.hash = hash;
            this.contentType = contentType;
        }

        public FileContentType getContentType() {
            return contentType;
        }

        public HashCode getHash() {
            return hash;
        }
    }
}
