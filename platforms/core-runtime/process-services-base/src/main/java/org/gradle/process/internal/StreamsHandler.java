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

package org.gradle.process.internal;

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
}
