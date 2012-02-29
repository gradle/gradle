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

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.UncheckedException;
import org.gradle.launcher.daemon.protocol.CloseInput;
import org.gradle.launcher.daemon.protocol.ForwardInput;
import org.gradle.messaging.concurrent.ExecutorFactory;
import org.gradle.messaging.concurrent.StoppableExecutor;
import org.gradle.messaging.dispatch.AsyncReceive;
import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.util.StdinSwapper;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

/**
 * Listens for ForwardInput commands during the execution and sends that to a piped input stream
 * that we install.
 */
public class ForwardClientInput implements DaemonCommandAction {
    private static final Logger LOGGER = Logging.getLogger(ForwardClientInput.class);
    private final ExecutorFactory executorFactory;

    public ForwardClientInput(ExecutorFactory executorFactory) {
        this.executorFactory = executorFactory;
    }

    public void execute(final DaemonCommandExecution execution) {
        final PipedOutputStream inputSource = new PipedOutputStream();
        final PipedInputStream replacementStdin;
        try {
            replacementStdin = new PipedInputStream(inputSource);
        } catch (IOException e) {
            throw new GradleException("unable to wire client stdin to daemon stdin", e);
        }

        final CountDownLatch inputOrConnectionClosedLatch = new CountDownLatch(1);
        final Runnable countDownInputOrConnectionClosedLatch = new Runnable() {
            public void run() {
                inputOrConnectionClosedLatch.countDown();
            }
        };

        Dispatch<Object> dispatcher = new Dispatch<Object>() {
            public void dispatch(Object command) {
                if (command instanceof ForwardInput) {
                    try {
                        ForwardInput forwardedInput = (ForwardInput)command;
                        LOGGER.debug("Putting forwarded input '{}' on daemon's stdin.", new String(forwardedInput.getBytes()).replace("\n", "\\n"));
                        inputSource.write(forwardedInput.getBytes());

                    } catch (Exception e) {
                        LOGGER.warn("Received exception trying to forward client input.", e);
                    }
                } else if (command instanceof CloseInput) {
                    try {
                        LOGGER.info("Closing daemons standard input as requested by received command: {} ...", command);
                        inputSource.close();
                    } catch (Throwable e) {
                        LOGGER.warn("Problem closing output stream connected to replacement stdin", e);
                    } finally {
                        LOGGER.info("The daemon will no longer process any standard input.");
                        countDownInputOrConnectionClosedLatch.run();
                    }
                } else {
                    LOGGER.warn("While listening for IOCommands, received unexpected command: {}.", command);
                }
            }
        };

        StoppableExecutor inputReceiverExecuter = executorFactory.create("daemon client input forwarder");
        final AsyncReceive<Object> inputReceiver = new AsyncReceive<Object>(inputReceiverExecuter, dispatcher, countDownInputOrConnectionClosedLatch);
        inputReceiver.receiveFrom(execution.getConnection());

        execution.addFinalizer(new Runnable() {
            public void run() {
                // means we are going to sit here until the client disconnects, which we are expecting it to
                // very soon because we are assuming we've just sent back the build result. We do this here
                // in case the client tries to send input in between us sending back the result and it closing the connection.
                try {
                    LOGGER.debug("Waiting until the client disconnects so that we may no longer consume input...");
                    inputOrConnectionClosedLatch.await();
                } catch (InterruptedException e) {
                    LOGGER.debug("Interrupted while waiting for client to disconnect.");
                    throw UncheckedException.throwAsUncheckedException(e);
                } finally {
                    inputReceiver.stop();
                    LOGGER.debug("The input receiver has been stopped.");
                }
            }
        });

        try {
            new StdinSwapper().swap(replacementStdin, new Callable<Void>() {
                public Void call() {
                    execution.proceed();
                    return null;
                }
            });
            replacementStdin.close();
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}