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

import org.gradle.api.Action;
import org.gradle.internal.UncheckedException;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An {@code InputStream} which reads from the source {@code InputStream}. In addition, when the {@code InputStream} is
 * closed, all threads blocked reading from the stream will receive an end-of-stream.
 */
public class DisconnectableInputStream extends BulkReadInputStream {
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final byte[] buffer;
    private int readPos;
    private int writePos;
    private boolean closed;
    private boolean inputFinished;

    /*
        The song and dance with Action<Runnable> is to ease testing.
        See DisconnectableInputStreamTest
     */

    static class ThreadExecuter implements Action<Runnable> {
        public void execute(Runnable runnable) {
            Thread thread = new Thread(runnable);
            thread.setName("DisconnectableInputStream source reader");
            thread.setDaemon(true);
            thread.start();
        }
    }

    public DisconnectableInputStream(InputStream source) {
        this(source, 1024);
    }

    public DisconnectableInputStream(final InputStream source, int bufferLength) {
        this(source, new ThreadExecuter(), bufferLength);
    }

    DisconnectableInputStream(InputStream source, Action<Runnable> executer) {
        this(source, executer, 1024);
    }

    DisconnectableInputStream(final InputStream source, Action<Runnable> executer, int bufferLength) {
        buffer = new byte[bufferLength];
        Runnable consume = new Runnable() {
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
                            if (nread < 0) {
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
                    throw UncheckedException.throwAsUncheckedException(throwable);
                }
            }
        };

        executer.execute(consume);
    }

    @Override
    public int read(byte[] bytes, int pos, int count) throws IOException {
        lock.lock();
        try {
            while (!inputFinished && !closed && readPos == writePos) {
                condition.await();
            }
            if (closed) {
                return -1;
            }

            // Drain the buffer before returning end-of-stream
            if (writePos > readPos) {
                int nread = Math.min(count, writePos - readPos);
                System.arraycopy(buffer, readPos, bytes, pos, nread);
                readPos += nread;
                assert writePos >= readPos;
                condition.signalAll();
                return nread;
            }

            assert inputFinished;
            return -1;
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Closes this {@code InputStream} for reading. Any threads blocked in read() will receive an end-of-stream. Also requests that the
     * reader thread stop reading, if possible, but does not block waiting for this to happen.
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
