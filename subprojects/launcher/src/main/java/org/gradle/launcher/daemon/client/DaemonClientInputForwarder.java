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
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.Stoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.id.IdGenerator;
import org.gradle.launcher.daemon.protocol.CloseInput;
import org.gradle.launcher.daemon.protocol.ForwardInput;
import org.gradle.launcher.daemon.protocol.IoCommand;
import org.gradle.messaging.dispatch.Dispatch;

import java.io.InputStream;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Eagerly consumes from an input stream, sending line by line ForwardInput
 * commands over the connection and finishing with a CloseInput command.»
 */
public class DaemonClientInputForwarder implements Stoppable {

    private static final Logger LOGGER = Logging.getLogger(DaemonClientInputForwarder.class);

    public static final int DEFAULT_BUFFER_SIZE = 1024;

    private final Lock lifecycleLock = new ReentrantLock();
    private boolean started;

    private final InputStream inputStream;
    private final Dispatch<? super IoCommand> dispatch;
    private final ExecutorFactory executorFactory;
    private final IdGenerator<?> idGenerator;
    private final int bufferSize;

    private InputForwarder forwarder;

    public DaemonClientInputForwarder(InputStream inputStream, Dispatch<? super IoCommand> dispatch, ExecutorFactory executorFactory, IdGenerator<?> idGenerator) {
        this(inputStream, dispatch, executorFactory, idGenerator, DEFAULT_BUFFER_SIZE);
    }

    public DaemonClientInputForwarder(InputStream inputStream, Dispatch<? super IoCommand> dispatch, ExecutorFactory executorFactory, IdGenerator<?> idGenerator, int bufferSize) {
        this.inputStream = inputStream;
        this.dispatch = dispatch;
        this.executorFactory = executorFactory;
        this.idGenerator = idGenerator;
        this.bufferSize = bufferSize;
    }

    public DaemonClientInputForwarder start() {
        lifecycleLock.lock();
        try {
            if (started) {
                throw new IllegalStateException("DaemonClientInputForwarder already started");
            }

            Action<String> dispatcher = new Action<String>() {
                public void execute(String input) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Forwarding input to daemon: '{}'", input.replace("\n", "\\n"));
                    }                    
                    dispatch.dispatch(new ForwardInput(idGenerator.generateId(), input.getBytes()));
                }
            };

            Runnable onFinish = new Runnable() {
                public void run() {
                    CloseInput message = new CloseInput(idGenerator.generateId());
                    LOGGER.debug("Dispatching close input message: {}", message);
                    dispatch.dispatch(message);
                }
            };

            forwarder = new InputForwarder(inputStream, dispatcher, onFinish, executorFactory, bufferSize).start();
            started = true;
            return this;
        } finally {
            lifecycleLock.unlock();
        }
    }

    public void stop() {
        lifecycleLock.lock();
        try {
            if (started) {
                LOGGER.debug("input forwarder stop requested");
                try {
                    forwarder.stop();
                } finally {
                    forwarder = null;
                    started = false;
                }
            }
        } finally {
            lifecycleLock.unlock();
        }
    }
}