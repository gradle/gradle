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

package org.gradle.launcher.daemon.server.clientinput;

import org.gradle.internal.UncheckedException;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.ReadStdInEvent;

import java.io.IOException;
import java.io.InputStream;

class StdInStream extends InputStream {
    private final OutputEventListener eventDispatch;
    private final Object lock = new Object();
    private final byte[] buffer = new byte[16 * 1024];
    private int readPos;
    private int writePos;
    private boolean waiting;
    private boolean closed;

    public StdInStream(OutputEventListener eventDispatch) {
        this.eventDispatch = eventDispatch;
    }

    @Override
    public int read() throws IOException {
        synchronized (lock) {
            waitForContent();
            if (readPos != writePos) {
                return buffer[readPos++] & 0xFF;
            } else {
                // Closed
                return -1;
            }
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        synchronized (lock) {
            waitForContent();
            if (readPos != writePos) {
                int count = Math.min(length, writePos - readPos);
                System.arraycopy(this.buffer, readPos, buffer, offset, count);
                readPos += count;
                return count;
            } else {
                // Closed
                return -1;
            }
        }
    }

    private void waitForContent() {
        if (readPos == writePos && !waiting) {
            eventDispatch.onOutput(new ReadStdInEvent(buffer.length));
            waiting = true;
        }
        while (readPos == writePos && !closed) {
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
            if (readPos == writePos) {
                readPos = 0;
                writePos = 0;
            } else {
                int count = Math.min(buffer.length - writePos, bytes.length);
                if (count != bytes.length) {
                    throw new IllegalStateException("Receive buffer overflow");
                }
            }
            System.arraycopy(bytes, 0, buffer, writePos, bytes.length);
            writePos += bytes.length;
            lock.notifyAll();
        }
    }
}
