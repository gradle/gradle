/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.launcher.daemon.client;

import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.concurrent.StoppableExecutor;
import org.gradle.internal.io.TextStream;
import org.gradle.util.DisconnectableInputStream;
import org.gradle.internal.io.LineBufferingOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.AsynchronousCloseException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Asynchronously consumes from an input stream for a time,
 * forwarding a <strong>line</strong> of input at a time to a specified action.
 *
 * Note that calling stop() will NOT close the source input stream.
 */
public class InputForwarder implements Stoppable {

    private final InputStream input;
    private final TextStream handler;
    private final ExecutorFactory executorFactory;
    private final int bufferSize;
    private StoppableExecutor forwardingExecuter;
    private DisconnectableInputStream disconnectableInput;
    private LineBufferingOutputStream outputBuffer;
    private final Lock lifecycleLock = new ReentrantLock();
    private boolean started;
    private boolean stopped;

    public InputForwarder(InputStream input, TextStream handler, ExecutorFactory executerFactory, int bufferSize) {
        this.input = input;
        this.handler = handler;
        this.executorFactory = executerFactory;
        this.bufferSize = bufferSize;
    }

    public InputForwarder start() {
        lifecycleLock.lock();
        try {
            if (started) {
                throw new IllegalStateException("input forwarder has already been started");
            }

            disconnectableInput = new DisconnectableInputStream(input, bufferSize);
            outputBuffer = new LineBufferingOutputStream(handler, bufferSize);

            forwardingExecuter = executorFactory.create("forward input");
            forwardingExecuter.execute(new Runnable() {
                public void run() {
                    byte[] buffer = new byte[bufferSize];
                    int readCount;
                    Throwable readFailure = null;
                    try {
                        while (true) {
                            try {
                                readCount = disconnectableInput.read(buffer, 0, bufferSize);
                                if (readCount < 0) {
                                    break;
                                }
                            } catch (AsynchronousCloseException e) {
                                break;
                            } catch (IOException e) {
                                readFailure = e;
                                break;
                            }

                            outputBuffer.write(buffer, 0, readCount);
                        }
                        outputBuffer.flush(); // will flush any unterminated lines out synchronously
                    } catch(IOException e) {
                        // should not happen
                        throw UncheckedException.throwAsUncheckedException(e);
                    } finally {
                        handler.endOfStream(readFailure);
                    }
                }
            });

            started = true;
        } finally {
            lifecycleLock.unlock();
        }

        return this;
    }

    public void stop() {
        lifecycleLock.lock();
        try {
            if (!stopped) {
                try {
                    disconnectableInput.close();
                } catch (IOException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }

                forwardingExecuter.stop();
                stopped = true;
            }
        } finally {
            lifecycleLock.unlock();
        }
    }

}
