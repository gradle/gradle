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
package org.gradle.launcher.daemon.server.exec;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.Factory;
import org.gradle.internal.IoActions;
import org.gradle.internal.UncheckedException;
import org.gradle.launcher.daemon.protocol.ForwardInput;
import org.gradle.launcher.daemon.server.api.DaemonCommandAction;
import org.gradle.launcher.daemon.server.api.DaemonCommandExecution;
import org.gradle.launcher.daemon.server.api.StdinHandler;
import org.gradle.util.StdinSwapper;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

/**
 * Listens for ForwardInput commands during the execution and sends that to a piped input stream that we install.
 */
public class ForwardClientInput implements DaemonCommandAction {
    private static final Logger LOGGER = Logging.getLogger(ForwardClientInput.class);

    @Override
    public void execute(final DaemonCommandExecution execution) {
        final PipedOutputStream inputSource = new PipedOutputStream();
        final PipedInputStream replacementStdin;
        try {
            replacementStdin = new PipedInputStream(inputSource);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

        execution.getConnection().onStdin(new StdinHandler() {
            @Override
            public void onInput(ForwardInput input) {
                LOGGER.debug("Writing forwarded input on daemon's stdin.");
                try {
                    inputSource.write(input.getBytes());
                } catch (IOException e) {
                    LOGGER.warn("Received exception trying to forward client input.", e);
                }
            }

            @Override
            public void onEndOfInput() {
                LOGGER.info("Closing daemon's stdin at end of input.");
                try {
                    inputSource.close();
                } catch (IOException e) {
                    LOGGER.warn("Problem closing output stream connected to replacement stdin", e);
                } finally {
                    LOGGER.info("The daemon will no longer process any standard input.");
                }
            }
        });

        try {
            try {
                new StdinSwapper().swap(replacementStdin, new Factory<Object>() {
                    @Override
                    public Void create() {
                        execution.proceed();
                        return null;
                    }
                });
            } finally {
                execution.getConnection().onStdin(null);
                IoActions.closeQuietly(replacementStdin);
                IoActions.closeQuietly(inputSource);
            }
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
