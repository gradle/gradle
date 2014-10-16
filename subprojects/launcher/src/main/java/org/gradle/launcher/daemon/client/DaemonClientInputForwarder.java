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
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.io.TextStream;
import org.gradle.launcher.daemon.protocol.CloseInput;
import org.gradle.launcher.daemon.protocol.ForwardInput;
import org.gradle.launcher.daemon.protocol.IoCommand;
import org.gradle.messaging.dispatch.Dispatch;

import java.io.InputStream;

/**
 * Eagerly consumes from an input stream, sending line by line ForwardInput
 * commands over the connection and finishing with a CloseInput command.
 * It also listens to cancel requests and forwards it too as Cancel command.
 */
public class DaemonClientInputForwarder implements Stoppable {
    private static final Logger LOGGER = Logging.getLogger(DaemonClientInputForwarder.class);

    public static final int DEFAULT_BUFFER_SIZE = 1024;
    private final InputForwarder forwarder;

    public DaemonClientInputForwarder(InputStream inputStream, Dispatch<? super IoCommand> dispatch,
                                      ExecutorFactory executorFactory, IdGenerator<?> idGenerator) {
        this(inputStream, dispatch, executorFactory, idGenerator, DEFAULT_BUFFER_SIZE);
    }

    public DaemonClientInputForwarder(InputStream inputStream, Dispatch<? super IoCommand> dispatch,
                                      ExecutorFactory executorFactory, IdGenerator<?> idGenerator, int bufferSize) {
        TextStream handler = new ForwardTextStreamToConnection(dispatch, idGenerator);
        forwarder = new InputForwarder(inputStream, handler, executorFactory, bufferSize);
    }

    public void start() {
        forwarder.start();
    }

    public void stop() {
        forwarder.stop();
    }

    private static class ForwardTextStreamToConnection implements TextStream {
        private final Dispatch<? super IoCommand> dispatch;
        private final IdGenerator<?> idGenerator;

        public ForwardTextStreamToConnection(Dispatch<? super IoCommand> dispatch, IdGenerator<?> idGenerator) {
            this.dispatch = dispatch;
            this.idGenerator = idGenerator;
        }

        public void text(String input) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Forwarding input to daemon: '{}'", input.replace("\n", "\\n"));
            }
            dispatch.dispatch(new ForwardInput(idGenerator.generateId(), input.getBytes()));
        }

        public void endOfStream(@Nullable Throwable failure) {
            CloseInput message = new CloseInput(idGenerator.generateId());
            LOGGER.debug("Dispatching close input message: {}", message);
            dispatch.dispatch(message);
        }
    }
}