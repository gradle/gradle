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

package org.gradle.internal.resource.transport.aws.s3;

import software.amazon.awssdk.core.ResponseInputStream;

import java.io.FilterInputStream;
import java.io.IOException;

/**
 * Releases the connection for reuse on close if the whole object was read, and aborts it otherwise, like SDK v1's S3AbortableInputStream.
 * <p>
 * The SDK's Apache client reads the rest of a partially read object on close,
 * so a cancelled or failed download of a large artifact would block until the rest of it arrived.
 */
final class AbortUnlessFullyReadInputStream extends FilterInputStream {
    private final ResponseInputStream<?> response;
    private final long contentLength;
    private long bytesRead;
    private boolean eofReached;
    private boolean closed;

    /**
     * @param contentLength the object's length, or -1 if unknown, in which case only reaching the end of the stream counts as fully read
     */
    AbortUnlessFullyReadInputStream(ResponseInputStream<?> response, long contentLength) {
        super(response);
        this.response = response;
        this.contentLength = contentLength;
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        if (eofReached || (contentLength >= 0 && bytesRead >= contentLength)) {
            response.close();
        } else {
            response.abort();
        }
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b == -1) {
            eofReached = true;
        } else {
            bytesRead++;
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = super.read(b, off, len);
        if (n == -1) {
            eofReached = true;
        } else {
            bytesRead += n;
        }
        return n;
    }

    @Override
    public long skip(long n) throws IOException {
        long skipped = super.skip(n);
        bytesRead += skipped;
        return skipped;
    }

    // A reset would make the byte count overstate how much was read
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
