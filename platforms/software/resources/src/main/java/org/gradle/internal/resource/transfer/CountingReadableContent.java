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

package org.gradle.internal.resource.transfer;

import org.gradle.api.resources.ResourceException;
import org.gradle.internal.resource.ReadableContent;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.LongConsumer;

/**
 * Counts the bytes read from content, each byte once, however many times the content is opened and read again.
 * <p>
 * Transports read content again to sign it or to retry a request, and open it again to skip to a part of a multipart upload.
 * Each opened stream tracks its position, and only the bytes past the furthest position any stream has read count.
 * Skipped bytes don't count. This assumes that the parts of the content are read in order.
 */
public final class CountingReadableContent implements ReadableContent {
    private final ReadableContent delegate;
    private final LongConsumer onCountedBytes;
    private long furthestPosition;
    private long count;

    public CountingReadableContent(ReadableContent delegate) {
        this(delegate, bytes -> {});
    }

    /**
     * @param onCountedBytes called with the number of newly counted bytes after each read that counts any
     */
    public CountingReadableContent(ReadableContent delegate, LongConsumer onCountedBytes) {
        this.delegate = delegate;
        this.onCountedBytes = onCountedBytes;
    }

    @Override
    public InputStream open() throws ResourceException {
        return new PositionTrackingInputStream(delegate.open());
    }

    @Override
    public long getContentLength() {
        return delegate.getContentLength();
    }

    public synchronized long getCount() {
        return count;
    }

    private void read(long position, long length) {
        long end = position + length;
        long newBytes;
        synchronized (this) {
            newBytes = end - Math.max(position, furthestPosition);
            if (newBytes <= 0) {
                return;
            }
            furthestPosition = end;
            count += newBytes;
        }
        onCountedBytes.accept(newBytes);
    }

    private final class PositionTrackingInputStream extends FilterInputStream {
        private long position;

        PositionTrackingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b != -1) {
                CountingReadableContent.this.read(position, 1);
                position++;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) {
                CountingReadableContent.this.read(position, n);
                position += n;
            }
            return n;
        }

        @Override
        public long skip(long n) throws IOException {
            long skipped = super.skip(n);
            position += skipped;
            return skipped;
        }

        // A reset would move the stream back without moving its position
        @Override
        public boolean markSupported() {
            return false;
        }

        @Override
        public synchronized void mark(int readlimit) {
        }

        @Override
        public synchronized void reset() throws IOException {
            throw new IOException("mark/reset not supported");
        }
    }
}
