/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.file.nio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;

/**
 * Similar to {@link java.nio.channels.Channels#newInputStream(ReadableByteChannel)}, but independently tracks the position. This allows multiple threads to read from different
 * positions in the same channel, without interfering with each other.
 */
public class PositionTrackingFileChannelInputStream extends InputStream {
    private final FileChannel channel;
    private long position;

    public PositionTrackingFileChannelInputStream(FileChannel channel, long position) {
        this.channel = channel;
        this.position = position;
    }

    @Override
    public int read() throws IOException {
        byte[] b = new byte[1];
        int read = read(b, 0, 1);
        return read == -1 ? -1 : b[0] & 0xff;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int read = channel.read(ByteBuffer.wrap(b, off, len), position);
        if (read > 0) {
            position += read;
        }
        return read;
    }
}
