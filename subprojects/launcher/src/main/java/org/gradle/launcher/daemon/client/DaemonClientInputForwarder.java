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

import org.gradle.api.Nullable;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.io.TextStream;
import org.gradle.launcher.daemon.protocol.Cancel;
import org.gradle.launcher.daemon.protocol.CloseInput;
import org.gradle.launcher.daemon.protocol.Command;
import org.gradle.launcher.daemon.protocol.ForwardInput;
import org.gradle.launcher.daemon.protocol.IoCommand;
import org.gradle.messaging.dispatch.Dispatch;

import java.io.InputStream;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Eagerly consumes from an input stream, sending line by line ForwardInput
 * commands over the connection and finishing with a CloseInput command.
 * It also listens to cancel requests and forwards it too as Cancel command.
 */
public class DaemonClientInputForwarder implements Stoppable {

    private static final Logger LOGGER = Logging.getLogger(DaemonClientInputForwarder.class);

    public static final int DEFAULT_BUFFER_SIZE = 1024;
    private final InputForwarder forwarder;
    private final Runnable cancellationCallback;
    private final ReentrantLock connectionMutex = new ReentrantLock();

    public DaemonClientInputForwarder(InputStream inputStream, Dispatch<? super Command> dispatch, BuildCancellationToken cancellationToken,
                                      ExecutorFactory executorFactory, IdGenerator<?> idGenerator) {
        this(inputStream, dispatch, cancellationToken, executorFactory, idGenerator, DEFAULT_BUFFER_SIZE);
    }

    public DaemonClientInputForwarder(InputStream inputStream, final Dispatch<? super Command> dispatch, BuildCancellationToken cancellationToken,
                                      ExecutorFactory executorFactory, final IdGenerator<?> idGenerator, int bufferSize) {
        TextStream handler = new ForwardTextStreamToConnection(dispatch, idGenerator, connectionMutex);
        forwarder = new InputForwarder(inputStream, handler, cancellationToken, executorFactory, bufferSize);
        cancellationCallback = new Runnable() {
            public void run() {
                connectionMutex.lock();
                try {
                    LOGGER.info("Request daemon to cancel build...");
                    dispatch.dispatch(new Cancel(idGenerator.generateId()));
                } finally {
                    connectionMutex.unlock();
                }
            }
        };

    }

    public DaemonClientInputForwarder start() {
        forwarder.start(cancellationCallback);
        return this;
    }

    public void stop() {
        forwarder.stop();
    }

    private static class ForwardTextStreamToConnection implements TextStream {
        private final Dispatch<? super IoCommand> dispatch;
        private final IdGenerator<?> idGenerator;
        private final ReentrantLock connectionMutex;

        public ForwardTextStreamToConnection(Dispatch<? super IoCommand> dispatch, IdGenerator<?> idGenerator, ReentrantLock connectionMutex) {
            this.dispatch = dispatch;
            this.idGenerator = idGenerator;
            this.connectionMutex = connectionMutex;
        }

        public void text(String input) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Forwarding input to daemon: '{}'", input.replace("\n", "\\n"));
            }
            connectionMutex.lock();
            try {
                dispatch.dispatch(new ForwardInput(idGenerator.generateId(), input.getBytes()));
            } finally {
                connectionMutex.unlock();
            }
        }

        public void endOfStream(@Nullable Throwable failure) {
            CloseInput message = new CloseInput(idGenerator.generateId());
            LOGGER.debug("Dispatching close input message: {}", message);
            connectionMutex.lock();
            try {
                dispatch.dispatch(message);
            } finally {
                connectionMutex.unlock();
            }
        }
    }
}