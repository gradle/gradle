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
package org.gradle.tooling.internal.consumer.async;

import org.gradle.tooling.internal.consumer.connection.ConsumerAction;
import org.gradle.tooling.internal.protocol.ResultHandlerVersion1;

/**
 * Implementations must be thread-safe.
 */
public interface AsyncConsumerActionExecutor {
    /**
     * Runs some operation asynchronously against a consumer connection. Notifies the provided handler when
     * complete. Note that the action may have completed by the time this method returns.
     *
     * @throws IllegalStateException When this connection has been stopped or is stopping.
     */
    <T> void run(ConsumerAction<? extends T> action, ResultHandlerVersion1<? super T> handler);

    /**
     * Stops this connection, blocking until all operations on the connection have completed.
     */
    void stop();

    String getDisplayName();

    /**
     * Requests cancellation on the current operation and send a 'stop when idle' message to the daemon.
     */
    void disconnect();
}
