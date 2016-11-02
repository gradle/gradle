/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.fixtures.executer;

import net.jcip.annotations.ThreadSafe;
import org.apache.commons.io.output.CloseShieldOutputStream;
import org.apache.commons.io.output.TeeOutputStream;
import org.gradle.internal.io.StreamByteBuffer;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@ThreadSafe
class OutputCapturer {
    private final StreamByteBuffer buffer;
    private final OutputStream outputStream;
    private final StringBuilder outputStringBuilder = new StringBuilder();
    private final String outputEncoding;
    private String cachedOutputString;
    private final Lock lock = new ReentrantLock();

    public OutputCapturer(OutputStream standardStream, String outputEncoding) {
        this.buffer = new StreamByteBuffer();
        this.outputStream = new CloseShieldOutputStream(new TeeOutputStream(standardStream, new LockingOutputStream(buffer.getOutputStream(), lock)));
        this.outputEncoding = outputEncoding;
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }

    public String getOutputAsString() {
        lock.lock();
        try {
            if (cachedOutputString == null || buffer.totalBytesUnread() > 0) {
                outputStringBuilder.append(buffer.readAsString(outputEncoding));
                cachedOutputString = outputStringBuilder.toString();
            }
            return cachedOutputString;
        } finally {
            lock.unlock();
        }
    }

    public void reset() {
        lock.lock();
        try {
            buffer.clear();
            outputStringBuilder.setLength(0);
            cachedOutputString = null;
        } finally {
            lock.unlock();
        }
    }

    private static class LockingOutputStream extends OutputStream {
        private final OutputStream delegate;
        private final Lock lock;

        LockingOutputStream(OutputStream delegate, Lock lock) {
            this.delegate = delegate;
            this.lock = lock;
        }

        @Override
        public void write(int b) throws IOException {
            lock.lock();
            try {
                delegate.write(b);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void write(byte[] b) throws IOException {
            lock.lock();
            try {
                delegate.write(b);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            lock.lock();
            try {
                delegate.write(b, off, len);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void flush() throws IOException {
            lock.lock();
            try {
                delegate.flush();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void close() throws IOException {
            lock.lock();
            try {
                delegate.close();
            } finally {
                lock.unlock();
            }
        }
    }
}
