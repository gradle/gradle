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
 * Represents a long-lived connection to a Gradle project. You obtain an instance of a {@code ProjectConnection} by using {@link org.gradle.tooling.GradleConnector#connect()}.
 *
 * <h2>Thread safety</h2>
 *
 * <p>All implementations of {@code ProjectConnection} are thread-safe, and may be shared by any number of threads.</p>
 *
 * <p>All notifications from a given {@code ProjectConnection} instance are delivered by a single thread at a time. Note, however, that the delivery thread may change over time.</p>
 */
public interface ProjectConnection {
    /**
     * Fetches a snapshot of the model of the given type for this project.
     *
     * <p>This method blocks until the model is available.
     *
     * @param viewType The model type.
     * @param <T> The model type.
     * @return The model.
     * @throws UnsupportedVersionException When the target Gradle version does not support the given model.
     * @throws BuildException On some failure executing the Gradle build, in order to build the model.
     * @throws GradleConnectionException On some other failure using the connection.
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
     * Creates a launcher which can be used to execute a build.
     *
     * @return The launcher.
     */
    BuildLauncher newBuild();

    /**
     * Creates a builder which can be used to build the model of the given type.
     *
     * @param modelType The model type
     * @param <T> The model type.
     * @return The builder.
     */
    <T extends Project> ModelBuilder<T> model(Class<T> modelType);

    /**
     * Closes this connection. Blocks until any pending operations are complete. Once this method has returned, no more notifications will be delivered by any threads.
     */
    void close();
}
