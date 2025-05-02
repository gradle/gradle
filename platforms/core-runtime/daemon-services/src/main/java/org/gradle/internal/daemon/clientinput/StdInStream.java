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

package org.gradle.internal.daemon.clientinput;

import org.gradle.internal.UncheckedException;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.ReadStdInEvent;

import java.io.IOException;
import java.io.InputStream;

/**
 * A replacement for {@code System.in} in the daemon. When read from, requests that the client read from its stdin stream and forward the result.
 */
class StdInStream extends InputStream {
    private final OutputEventListener eventDispatch;
    private final Object lock = new Object();
    private byte[] buffer = new byte[0];
    private int readPos;
    private boolean waiting;
    private boolean closed;

    public StdInStream(OutputEventListener eventDispatch) {
        this.eventDispatch = eventDispatch;
    }

    @Override
    public int read() throws IOException {
        synchronized (lock) {
            waitForContent();
            if (readPos != buffer.length) {
                return buffer[readPos++] & 0xFF;
            } else {
                // Closed
                return -1;
            }
        }
    }

    @Override
    public int read(byte[] dest, int offset, int length) throws IOException {
        synchronized (lock) {
            waitForContent();
            if (readPos != buffer.length) {
                int count = Math.min(length, buffer.length - readPos);
                System.arraycopy(buffer, readPos, dest, offset, count);
                readPos += count;
                return count;
            } else {
                // Closed
                return -1;
            }
        }
    }

    private void waitForContent() {
        if (readPos == buffer.length && !waiting && !closed) {
            eventDispatch.onOutput(new ReadStdInEvent());
            waiting = true;
        }
        while (readPos == buffer.length && !closed) {
            try {
                lock.wait();
            } catch (InterruptedException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            // Allow threads to read anything still buffered
            closed = true;
            lock.notifyAll();
        }
    }

    public void received(byte[] bytes) {
        synchronized (lock) {
            if (!waiting) {
                throw new IllegalStateException();
            }
            waiting = false;
            readPos = 0;
            this.buffer = bytes;
            lock.notifyAll();
        }
    }
}
