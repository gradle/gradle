/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.launcher.daemon.server.api;

import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.daemon.clientinput.StdinHandler;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.launcher.daemon.protocol.BuildStarted;
import org.gradle.launcher.daemon.protocol.DaemonUnavailable;
import org.gradle.launcher.daemon.protocol.Result;

import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;

public interface DaemonConnection extends Stoppable {
    /**
     * Registers a handler for incoming client stdin. The handler is notified from at most one thread at a time.
     *
     * The following events trigger an end of input:
     * <ul>
     * <li>A {@link org.gradle.launcher.daemon.protocol.CloseInput} event received from the client.</li>
     * <li>When the client connection disconnects unexpectedly.</li>
     * <li>When the connection is closed using {@link #stop()}.</li>
     * </ul>
     *
     * Note: the end of input may be signalled from another thread before this method returns.
     *
     * @param handler the handler. Use null to remove the current handler.
     */
    void onStdin(@Nullable StdinHandler handler);

    /**
     * Registers a handler for when this connection is disconnected unexpectedly.. The handler is notified at most once.
     *
     * The handler is not notified after any of the following occurs:
     * <ul>
     * <li>When the connection is closed using {@link #stop()}.</li>
     * </ul>
     *
     * Note: the handler may be run from another thread before this method returns.
     *
     * @param handler the handler. Use null to remove the current handler.
     */
    void onDisconnect(@Nullable Runnable handler);

    /**
     * Registers a handler for when this connection receives cancel command. The handler is notified at most once.
     *
     * The handler is not notified after any of the following occurs:
     * <ul>
     * <li>When the connection is closed using {@link #stop()}.</li>
     * </ul>
     *
     * Note: the handler may be run from another thread before this method returns.
     *
     * @param handler the handler. Use null to remove the current handler.
     */
    void onCancel(@Nullable Runnable handler);

    /**
     * Dispatches a daemon unavailable message to the client.
     */
    void daemonUnavailable(DaemonUnavailable unavailable);

    /**
     * Dispatches a build started message to the client.
     */
    void buildStarted(BuildStarted buildStarted);

    /**
     * Dispatches a log event message to the client.
     */
    void logEvent(OutputEvent logEvent);

    /**
     * Dispatches some build event to the client.
     */
    void event(Object event);

    /**
     * Dispatches the given result to the client.
     */
    void completed(Result result);

    /**
     * Receives a message from the client. Does not include any stdin messages. Blocks until a message is received or the connection is closed or the timeout is reached.
     *
     * @return null On end of connection or timeout.
     */
    Object receive(long timeoutValue, TimeUnit timeoutUnits);

    /**
     * Blocks until all handlers have been notified and any queued messages have been dispatched to the client.
     */
    @Override
    void stop();
}
