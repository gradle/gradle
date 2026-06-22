/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.process.internal.streams;

import org.gradle.internal.concurrent.Stoppable;

import java.util.concurrent.Executor;

public interface StreamsHandler extends Stoppable {
    /**
     * Collects whatever state is required the given process. Should not start work.
     */
    void connectStreams(Process process, String processName, Executor executor);

    /**
     * Starts reading/writing/whatever the process' streams. May block until the streams reach some particular state, e.g. indicate that the process has started successfully.
     */
    void start();

    /**
     * Remove any context associated with tracking the startup of the process.
     */
    void removeStartupContext();

    /**
     * Disconnects from the process without waiting for further work.
     */
    void disconnect();

    /**
     * Stops doing work with the process's streams. Should block until no further asynchronous work is happening on the streams.
     */
    @Override
    void stop();

    /**
     * Like {@link #stop()}, but waits at most {@code timeoutMillis} for the asynchronous work on the process's streams to finish.
     *
     * <p>A forwarder reading the process' stdout/stderr can stay parked in an uninterruptible native {@code read()} when a
     * surviving child process (for example a Gradle daemon started by the process) keeps the output pipe open after the process
     * itself has exited. This variant lets callers put an upper bound on how long they wait for such draining before giving up.</p>
     *
     * @param timeoutMillis the maximum time to wait, in milliseconds
     * @return {@code true} if all work on the streams finished within the timeout, {@code false} if work is still ongoing
     */
    default boolean stop(long timeoutMillis) {
        stop();
        return true;
    }
}
