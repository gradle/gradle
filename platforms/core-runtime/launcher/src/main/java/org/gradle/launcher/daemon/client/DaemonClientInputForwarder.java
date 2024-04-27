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
import org.gradle.internal.dispatch.Dispatch;
import org.gradle.internal.logging.console.GlobalUserInputReceiver;
import org.gradle.internal.logging.console.UserInputReceiver;
import org.gradle.launcher.daemon.protocol.CloseInput;
import org.gradle.launcher.daemon.protocol.ForwardInput;
import org.gradle.launcher.daemon.protocol.InputMessage;
import org.gradle.launcher.daemon.protocol.UserResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.Executor;

public class DaemonClientInputForwarder implements Stoppable {
    private final ForwardingUserInput forwarder;
    private final GlobalUserInputReceiver userInput;

    public DaemonClientInputForwarder(
        InputStream inputStream,
        Dispatch<? super InputMessage> dispatch,
        GlobalUserInputReceiver userInput,
        ExecutorFactory executorFactory
    ) {
        this.userInput = userInput;
        forwarder = new ForwardingUserInput(inputStream, dispatch, executorFactory.create("Stdin"));
        userInput.dispatchTo(forwarder);
    }

    public void start() {
    }

    @Override
    public void stop() {
        userInput.stopDispatching();
        forwarder.stop();
    }

    private static class ForwardingUserInput implements UserInputReceiver {
        private final Dispatch<? super InputMessage> dispatch;
        private final BufferedReader reader;
        private final Executor executor;

        public ForwardingUserInput(InputStream inputStream, Dispatch<? super InputMessage> dispatch, Executor executor) {
            this.dispatch = dispatch;
            this.reader = new BufferedReader(new InputStreamReader(inputStream));
            this.executor = executor;
        }

        @Override
        public void readAndForwardStdin(int maxLength) {
            executor.execute(() -> {
                char[] buffer = new char[16 * 1024];
                int nread;
                try {
                    nread = reader.read(buffer);
                } catch (IOException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
                if (nread < 0) {
                    CloseInput message = new CloseInput();
                    dispatch.dispatch(message);
                } else {
                    String text = new String(buffer, 0, nread);
                    byte[] result = text.getBytes();
                    ForwardInput message = new ForwardInput(result);
                    dispatch.dispatch(message);
                }
            });
        }

        @Override
        public void readAndForwardText(Normalizer normalizer) {
            executor.execute(() -> {
                while (true) {
                    String input;
                    try {
                        input = reader.readLine();
                    } catch (IOException e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    }
                    if (input == null) {
                        CloseInput message = new CloseInput();
                        dispatch.dispatch(message);
                        break;
                    } else {
                        String normalized = normalizer.normalize(input);
                        if (normalized != null) {
                            dispatch.dispatch(new UserResponse(normalized));
                            break;
                        }
                        // Else, user input was no good
                    }
                }
            });
        }

        void stop() {
            dispatch.dispatch(new CloseInput());
        }
    }
}
