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

import java.io.OutputStream;

/**
 * <p>A {@code ModelBuilder} allows you to fetch a snapshot of the model for a project.
 *
 * <p>You use a {@code ModelBuilder} as follows:
 *
 * <ul>
 *
 * <li>Create an instance of {@code ModelBuilder} by calling {@link org.gradle.tooling.ProjectConnection#model(Class)}.
 *
 * <li>Configure the builder as appropriate.
 *
 * <li>Call either {@link #get()} or {@link #get(ResultHandler)} to build the model.
 *
 * <li>Optionally, you can reuse the builder to build the model multiple times.
 *
 * </ul>
 *
 * <p>Instances of {@code ModelBuilder} are not thread-safe.
 *
 * @param <T> The type of model to build
 */
public interface ModelBuilder<T extends Project> {
    /**
     * Sets the {@link OutputStream} which should receive standard output logging generated while building the model. The default is to discard the output.
     *
     * @param outputStream The output stream.
     * @return this
     */
    ModelBuilder<T> setStandardOutput(OutputStream outputStream);

    /**
     * Sets the {@link OutputStream} which should receive standard error logging generated while building the model. The default is to discard the output.
     *
     * @param outputStream The output stream.
     * @return this
     */
    ModelBuilder<T> setStandardError(OutputStream outputStream);

    /**
     * Adds a progress listener which will receive progress events as the model is being built.
     *
     * @param listener The listener
     * @return this
     */
    ModelBuilder<T> addProgressListener(ProgressListener listener);

    /**
     * Fetch the model, blocking until it is available.
     *
     * @return The model.
     * @throws UnsupportedVersionException When the target Gradle version does not support the features required to build this model.
     * @throws BuildException On some failure executing the Gradle build.
     * @throws GradleConnectionException On some other failure using the connection.
     * @throws IllegalStateException When the connection has been closed or is closing.
     */
    T get() throws GradleConnectionException;

    /**
     * Starts fetching the build. This method returns immediately, and the result is later passed to the given handler.
     *
     * @param handler The handler to supply the result to.
     * @throws IllegalStateException When the connection has been closed or is closing.
     */
    void get(ResultHandler<? super T> handler) throws IllegalStateException;
}
