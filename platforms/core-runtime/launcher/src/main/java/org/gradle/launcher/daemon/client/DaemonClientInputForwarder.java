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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.dispatch.Dispatch;
import org.gradle.internal.io.TextStream;
import org.gradle.internal.logging.console.GlobalUserInputReceiver;
import org.gradle.internal.logging.console.UserInputReceiver;
import org.gradle.launcher.daemon.protocol.CloseInput;
import org.gradle.launcher.daemon.protocol.ForwardInput;
import org.gradle.launcher.daemon.protocol.InputMessage;
import org.gradle.launcher.daemon.protocol.UserResponse;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Eagerly consumes from an input stream, sending each line as a commands over the connection and finishing with a {@link CloseInput} command.
 * Each line is forwarded as either a {@link ForwardInput}, for non-interactive text, or a {@link UserResponse}, when expecting some interactive text.
 */
public class DaemonClientInputForwarder implements Stoppable {
    private static final Logger LOGGER = Logging.getLogger(DaemonClientInputForwarder.class);

    public static final int DEFAULT_BUFFER_SIZE = 8192;
    private final InputForwarder forwarder;
    private final GlobalUserInputReceiver userInput;

    public DaemonClientInputForwarder(
        InputStream inputStream,
        Dispatch<? super InputMessage> dispatch,
        GlobalUserInputReceiver userInput,
        ExecutorFactory executorFactory
    ) {
        this(inputStream, dispatch, userInput, executorFactory, DEFAULT_BUFFER_SIZE);
    }

    public DaemonClientInputForwarder(
        InputStream inputStream,
        Dispatch<? super InputMessage> dispatch,
        GlobalUserInputReceiver userInput,
        ExecutorFactory executorFactory,
        int bufferSize
    ) {
        this.userInput = userInput;
        ForwardTextStreamToConnection handler = new ForwardTextStreamToConnection(dispatch);
        this.forwarder = new InputForwarder(inputStream, handler, executorFactory, bufferSize);
        userInput.dispatchTo(new ForwardingUserInput(handler));
    }

    public void start() {
        forwarder.start();
    }

    @Override
    public void stop() {
        userInput.stopDispatching();
        forwarder.stop();
    }

    private static class ForwardingUserInput implements UserInputReceiver {
        private final ForwardTextStreamToConnection handler;

        public ForwardingUserInput(ForwardTextStreamToConnection handler) {
            this.handler = handler;
        }

        @Override
        public void readAndForwardText(Normalizer normalizer) {
            handler.forwardNextLineAsUserResponse(normalizer);
        }
    }

    private static class ForwardTextStreamToConnection implements TextStream {
        private final Dispatch<? super InputMessage> dispatch;
        private final AtomicReference<UserInputReceiver.Normalizer> pending = new AtomicReference<>();

        public ForwardTextStreamToConnection(Dispatch<? super InputMessage> dispatch) {
            this.dispatch = dispatch;
        }

        void forwardNextLineAsUserResponse(UserInputReceiver.Normalizer request) {
            if (!pending.compareAndSet(null, request)) {
                throw new IllegalStateException("Already expecting user input");
            }
        }

        @Override
        public void text(String input) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Forwarding input to daemon: '{}'", input.replace("\n", "\\n"));
            }
            UserInputReceiver.Normalizer currentUserInputRequest = pending.get();
            if (currentUserInputRequest != null) {
                // Expecting some user input
                String result = currentUserInputRequest.normalize(input);
                if (result != null) {
                    // Send result
                    pending.set(null);
                    dispatch.dispatch(new UserResponse(result));
                }
                // Else, input was no good, so try again
            } else {
                dispatch.dispatch(new ForwardInput(input.getBytes()));
            }
        }

        @Override
        public void endOfStream(@Nullable Throwable failure) {
            CloseInput message = new CloseInput();
            LOGGER.debug("Dispatching close input message: {}", message);
            dispatch.dispatch(message);
        }
    }
}
