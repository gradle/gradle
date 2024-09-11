/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.internal.daemon.client.clientinput;

import org.gradle.internal.UncheckedException;
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
import java.nio.charset.Charset;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reads input from this client stdin and forwards it to the daemon. Can either read raw content or read the user's response to some prompt.
 *
 * <p>Uses a single reader thread to perform all operations.
 */
public class DaemonClientInputForwarder implements Stoppable {
    private final ForwardingUserInput forwarder;
    private final GlobalUserInputReceiver userInput;
    private final ExecutorService executor;

    public DaemonClientInputForwarder(
        InputStream inputStream,
        Dispatch<? super InputMessage> dispatch,
        GlobalUserInputReceiver userInput
    ) {
        this.userInput = userInput;
        // Use a single reader thread, and make it a daemon thread so that it does not block process shutdown
        // In most cases, we try to cleanly shut down all threads. However, in this case it is difficult to disconnect a thread blocked trying to read from the
        // process' stdin, so use a daemon thread instead.
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            return thread;
        });
        forwarder = new ForwardingUserInput(inputStream, dispatch, executor);
        userInput.dispatchTo(forwarder);
    }

    @Override
    public void stop() {
        userInput.stopDispatching();
        forwarder.stop();
        executor.shutdown();
    }

    private static class ForwardingUserInput implements UserInputReceiver {
        private final Dispatch<? super InputMessage> dispatch;
        private final BufferedReader reader;
        private final Executor executor;
        private char[] buffer;

        // Protects the following state
        private final Object lock = new Object();
        private boolean closed;

        public ForwardingUserInput(InputStream inputStream, Dispatch<? super InputMessage> dispatch, Executor executor) {
            this.dispatch = dispatch;
            this.reader = new BufferedReader(new InputStreamReader(inputStream, Charset.defaultCharset()));
            this.executor = executor;
        }

        @Override
        public void readAndForwardStdin() {
            executor.execute(() -> {
                if (buffer == null) {
                    buffer = new char[16 * 1024];
                }
                int nread;
                try {
                    // Read input as text rather than bytes, so that readAndForwardText() below can also read lines of text
                    nread = reader.read(buffer);
                } catch (IOException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
                if (nread < 0) {
                    maybeClosed();
                } else {
                    String text = new String(buffer, 0, nread);
                    byte[] result = text.getBytes(Charset.defaultCharset());
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
                        maybeClosed();
                        break;
                    } else {
                        String normalized = normalizer.normalize(input);
                        if (normalized != null) {
                            dispatch.dispatch(new UserResponse(normalized));
                            break;
                        }
                        // Else, user input was no good so read another line and try again
                    }
                }
            });
        }

        void stop() {
            maybeClosed();
        }

        private void maybeClosed() {
            // This can be invoked from the reader thread or some other thread that is attempting to shut the client down.
            synchronized (lock) {
                if (!closed) {
                    CloseInput message = new CloseInput();
                    dispatch.dispatch(message);
                    closed = true;
                }
            }
        }
    }
}
