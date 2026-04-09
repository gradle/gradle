/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.cache.internal.loghash;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * An OutputStream that writes to a FileChannel at a given position via positional writes (pwrite).
 * Reusable across puts by calling {@link #reset(FileChannel, long)}.
 *
 * <p>Caches ByteBuffer wrappers to avoid allocation per write call. The bulk-write buffer is
 * reused when the backing array is the same (KryoBackedEncoder reuses its internal buffer,
 * so this almost never reallocates).</p>
 */
class PositionalOutputStream extends OutputStream {

    private final byte[] singleByte = new byte[1];
    private final ByteBuffer singleByteBuf = ByteBuffer.wrap(singleByte);
    private byte[] cachedArray = singleByte;
    private ByteBuffer cachedBuf = singleByteBuf;
    private FileChannel channel;
    private long position;
    private int bytesWritten;

    void reset(FileChannel channel, long position) {
        this.channel = channel;
        this.position = position;
        this.bytesWritten = 0;
    }

    int getBytesWritten() {
        return bytesWritten;
    }

    @Override
    public void write(int b) throws IOException {
        singleByte[0] = (byte) b;
        singleByteBuf.clear();
        int n = channel.write(singleByteBuf, position);
        position += n;
        bytesWritten += n;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        ByteBuffer buf = cachedBuf;
        if (cachedArray != b) {
            buf = ByteBuffer.wrap(b);
            cachedArray = b;
            cachedBuf = buf;
        }
        buf.limit(off + len).position(off);
        while (buf.hasRemaining()) {
            int n = channel.write(buf, position);
            position += n;
            bytesWritten += n;
        }
    }
}
