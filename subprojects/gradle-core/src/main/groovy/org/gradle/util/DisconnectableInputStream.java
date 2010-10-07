/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.util;

import org.gradle.messaging.concurrent.ExecutorFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An {@code InputStream} which reads from the source {@code InputStream}. In addition, when the {@code InputStream} is
 * closed, all threads blocked reading from the stream will receive an end-of-stream.
 */
public class DisconnectableInputStream extends InputStream {
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final byte[] buffer;
    private int readPos;
    private int writePos;
    private boolean closed;
    private boolean inputFinished;

    public DisconnectableInputStream(final InputStream source, ExecutorFactory executorFactory) {
        this(source, executorFactory, 1024);
    }

    public DisconnectableInputStream(final InputStream source, ExecutorFactory executorFactory, int bufferLength) {
        buffer = new byte[bufferLength];
        executorFactory.create("read input").execute(new Runnable() {
            public void run() {
                try {
                    while (true) {
                        int pos;
                        lock.lock();
                        try {
                            while (!closed && writePos == buffer.length && writePos != readPos) {
                                // buffer is full, wait until it has been read
                                condition.await();
                            }
                            assert writePos >= readPos;
                            if (closed) {
                                // stream has been closed, don't bother reading anything else
                                inputFinished = true;
                                condition.signalAll();
                                return;
                            }
                            if (readPos == writePos) {
                                // buffer has been fully read, start at the beginning
                                readPos = 0;
                                writePos = 0;
                            }
                            pos = writePos;
                        } finally {
                            lock.unlock();
                        }

                        int nread = source.read(buffer, pos, buffer.length - pos);

                        lock.lock();
                        try {
                            if (nread > 0) {
                                // Have read some data - let readers know
                                assert writePos >= readPos;
                                writePos += nread;
                                assert buffer.length >= writePos;
                                condition.signalAll();
                            }
                            if (closed || nread < 0) {
                                // End of the stream
                                inputFinished = true;
                                condition.signalAll();
                                return;
                            }
                        } finally {
                            lock.unlock();
                        }
                    }
                } catch (Throwable throwable) {
                    lock.lock();
                    try {
                        inputFinished = true;
                        condition.signalAll();
                    } finally {
                        lock.unlock();
                    }
                    throw UncheckedException.asUncheckedException(throwable);
                }
            }
        });
    }

    @Override
    public int read() throws IOException {
        byte[] buffer = new byte[1];
        while (true) {
            int nread = read(buffer);
            if (nread < 0) {
                return -1;
            }
            if (nread == 1) {
                return 0xff & buffer[0];
            }
        }
    }

    @Override
    public int read(byte[] bytes, int pos, int count) throws IOException {
        lock.lock();
        int nread;
        try {
            while (!inputFinished && !closed && readPos == writePos) {
                condition.await();
            }
            if (inputFinished && readPos == writePos) {
                return -1;
            }
            if (closed) {
                return -1;
            }
            assert writePos > readPos;
            nread = Math.min(count, writePos - readPos);
            System.arraycopy(buffer, readPos, bytes, pos, nread);
            readPos += nread;
            assert writePos >= readPos;
            condition.signalAll();
        } catch (InterruptedException e) {
            throw UncheckedException.asUncheckedException(e);
        } finally {
            lock.unlock();
        }
        return nread;
    }

    /**
     * Closes this {@code InputStream} for reading. Any threads blocked in read() will receive a {@link
     * java.nio.channels.AsynchronousCloseException}. Also requests that the reader thread stop reading, if possible,
     * but does not block waiting for this to happen.
     *
     * <p>NOTE: this method does not close the source input stream.</p>
     */
    @Override
    public void close() throws IOException {
        lock.lock();
        try {
            closed = true;
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }
}
