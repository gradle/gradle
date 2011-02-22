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
package org.gradle.tooling;

import org.gradle.tooling.model.Project;

/**
 * Represents a long-lived connection to a Gradle project.
 *
 * <h2>Thread safety</h2>
 *
 * <p>All implementations of {@code GradleConnection} are thread-safe, and may be shared by any number of threads.</p>
 *
 * <p>All notifications from a given {@code GradleConnection} instance are delivered by a single thread at a time. Note, however, that the delivery thread may change over time.</p>
 */
public interface ProjectConnection {
    /**
     * Fetches a snapshot of the model for this project. This method blocks until the model is available.
     *
     * @param viewType The model type.
     * @param <T> The model type.
     * @return The model.
     * @throws UnsupportedVersionException When the target Gradle version does not support the given model.
     * @throws GradleConnectionException On some failure to communicate with Gradle.
     * @throws IllegalStateException When this connection has been closed or is closing.
     */
    <T extends Project> T getModel(Class<T> viewType) throws GradleConnectionException;

    /**
     * Fetches a snapshot of the model for this project asynchronously. This method return immediately, and the result of the operation is passed to the supplied result handler.
     *
     * @param viewType The model type.
     * @param handler The handler to pass the result to.
     * @param <T> The model type.
     * @throws IllegalStateException When this connection has been closed or is closing.
     */
    <T extends Project> void getModel(Class<T> viewType, ResultHandler<? super T> handler) throws IllegalStateException;

    /**
     * Closes this connection. Blocks until the close is complete. Once this method has returned, no more notifications will be delivered by any threads.
     */
    void close();
}
