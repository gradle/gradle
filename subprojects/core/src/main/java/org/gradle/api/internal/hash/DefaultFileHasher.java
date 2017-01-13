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
package org.gradle.api.internal.hash;

import com.google.common.base.Charsets;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileTreeElement;
import org.gradle.internal.nativeintegration.filesystem.FileMetadataSnapshot;
import org.gradle.internal.resource.TextResource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

public class DefaultFileHasher implements FileHasher {
    private static final byte[] SIGNATURE = Hashing.md5().hashString(DefaultFileHasher.class.getName(), Charsets.UTF_8).asBytes();
    private final Queue<byte[]> buffers = new ArrayBlockingQueue<byte[]>(16);

    @Override
    public HashCode hash(InputStream inputStream) {
        try {
            return doHash(inputStream);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create MD5 hash for file content.", e);
        }
    }

    @Override
    public HashCode hash(TextResource resource) {
        Hasher hasher = createFileHasher();
        hasher.putString(resource.getText(), Charsets.UTF_8);
        return hasher.hash();
    }

    @Override
    public HashCode hash(File file) {
        try {
            InputStream inputStream = new FileInputStream(file);
            return doHash(inputStream);
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("Failed to create MD5 hash for file '%s'.", file), e);
        }
    }

    private HashCode doHash(InputStream inputStream) throws IOException {
        try {
            byte[] buffer = takeBuffer();
            try {
                Hasher hasher = createFileHasher();
                while (true) {
                    int nread = inputStream.read(buffer);
                    if (nread < 0) {
                        break;
                    }
                    hasher.putBytes(buffer, 0, nread);
                }
                return hasher.hash();
            } finally {
                returnBuffer(buffer);
            }
        } finally {
            inputStream.close();
        }
    }

    private void returnBuffer(byte[] buffer) {
        // Retain buffer if there is capacity in the queue, otherwise discard
        buffers.offer(buffer);
    }

    private byte[] takeBuffer() {
        byte[] buffer = buffers.poll();
        if (buffer == null) {
            buffer = new byte[8192];
        }
        return buffer;
    }

    @Override
    public HashCode hash(File file, FileMetadataSnapshot fileDetails) {
        return hash(file);
    }

    @Override
    public HashCode hash(FileTreeElement fileDetails) {
        return hash(fileDetails.getFile());
    }

    private static Hasher createFileHasher() {
        Hasher hasher = Hashing.md5().newHasher();
        hasher.putBytes(SIGNATURE);
        return hasher;
    }
}
