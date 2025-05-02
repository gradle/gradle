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
package org.gradle.internal.hash;

import com.google.common.io.ByteStreams;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

public class DefaultStreamHasher implements StreamHasher {
    private static final HashCode SIGNATURE = Hashing.signature(DefaultStreamHasher.class);

    private final Queue<byte[]> buffers = new ArrayBlockingQueue<byte[]>(16);

    @Override
    public HashCode hash(InputStream inputStream) throws IOException {
        return doHash(inputStream, ByteStreams.nullOutputStream());
    }

    @Override
    public HashCode hashCopy(InputStream inputStream, OutputStream outputStream) throws IOException {
        return doHash(inputStream, outputStream);
    }

    private HashCode doHash(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] buffer = takeBuffer();
        try {
            PrimitiveHasher hasher = Hashing.newPrimitiveHasher();
            hasher.putHash(SIGNATURE);
            while (true) {
                int nread = inputStream.read(buffer);
                if (nread < 0) {
                    break;
                }
                outputStream.write(buffer, 0, nread);
                hasher.putBytes(buffer, 0, nread);
            }
            return hasher.hash();
        } finally {
            returnBuffer(buffer);
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
}
