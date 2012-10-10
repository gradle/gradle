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

import org.gradle.api.Action;
import org.gradle.internal.Stoppable;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.StoppableExecutor;
import org.gradle.util.DisconnectableInputStream;
import org.gradle.util.LineBufferingOutputStream;

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
    private final Action<String> forwardTo;
    private final Runnable onFinish;
    private final ExecutorFactory executorFactory;
    private final int bufferSize;
    private StoppableExecutor forwardingExecuter;
    private DisconnectableInputStream disconnectableInput;
    private LineBufferingOutputStream outputBuffer;
    private final Lock lifecycleLock = new ReentrantLock();
    private boolean started;
    private boolean stopped;

    public InputForwarder(InputStream input, Action<String> forwardTo, Runnable onFinish, ExecutorFactory executerFactory, int bufferSize) {
        this.input = input;
        this.forwardTo = forwardTo;
        this.onFinish = onFinish;
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
            outputBuffer = new LineBufferingOutputStream(forwardTo, bufferSize);

            forwardingExecuter = executorFactory.create("forward input");
            forwardingExecuter.execute(new Runnable() {
                public void run() {
                    byte[] buffer = new byte[bufferSize];
                    int readCount;
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
                                // Unsure what the best thing to do is here, should we forward the error?
                                throw UncheckedException.throwAsUncheckedException(e);
                            }

                            try {
                                outputBuffer.write(buffer, 0, readCount);
                            } catch (IOException e) {
                                // this shouldn't happen as outputBuffer will only throw if close has been called
                                // and we own this object exclusively and will not have done that at this time
                                throw UncheckedException.throwAsUncheckedException(e);
                            }
                        }
                    } finally {
                        try {
                            outputBuffer.close(); // will flush any unterminated lines out synchronously
                        } catch (IOException e) {
                            throw UncheckedException.throwAsUncheckedException(e);
                        }
                    }
                    
                    onFinish.run();
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