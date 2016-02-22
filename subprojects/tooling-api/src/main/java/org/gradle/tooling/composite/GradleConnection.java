/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.tooling.composite;

import org.gradle.api.Incubating;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ResultHandler;

import java.io.File;
import java.net.URI;
import java.util.Set;

/**
 * Represents a connection to a composite Gradle build.
 *
 * <p>A composite build is a lightweight assembly of Gradle projects that a developer is working on.
 * These projects may come from different Gradle builds, but when assembled into a composite Gradle is
 * able to coordinate across these projects, so that they appear in some way as a single build unit.</p>
 *
 * <p>Operations (fetching models, executing tasks, etc) are performed across all Gradle projects in a composite.</p>
 */
@Incubating
public interface GradleConnection {
    /**
     * Builds a new composite Gradle connection.
     */
    interface Builder {
        /**
         * Specifies the user's Gradle home directory to use. Defaults to {@code ~/.gradle}.
         *
         * @param gradleUserHomeDir The user's Gradle home directory to use.
         * @return this
         */
        Builder useGradleUserHomeDir(File gradleUserHomeDir);

        /**
         * Adds a Gradle build as a participant in a composite.
         *
         * Defaults to a project-specific Gradle version.
         *
         * @param rootProjectDirectory Root directory of Gradle build
         *
         * @return this
         */
        Builder addBuild(File rootProjectDirectory);

        /**
         * Adds a Gradle build as a participant in a composite, specifying the Gradle distribution to use.
         *
         * @param rootProjectDirectory Root directory of Gradle build
         * @param gradleHome The Gradle installation directory.
         * @return this
         */
        Builder addBuild(File rootProjectDirectory, File gradleHome);

        /**
         * Adds a Gradle build as a participant in a composite, specifying the version of Gradle to use.
         *
         * @param rootProjectDirectory Root directory of Gradle build
         * @param gradleVersion The version to use.
         * @return this
         */
        Builder addBuild(File rootProjectDirectory, String gradleVersion);

        /**
         * Adds a Gradle build as a participant in a composite, specifying the Gradle distribution to use.
         *
         * @param rootProjectDirectory Root directory of Gradle build
         * @param gradleDistribution The distribution to use.
         *
         * @return this
         */
        Builder addBuild(File rootProjectDirectory, URI gradleDistribution);

        /**
         * Builds the connection. You should call {@link org.gradle.tooling.composite.GradleConnection#close()} when you are finished with the connection.
         *
         * @return The connection. Never returns null.
         * @throws GradleConnectionException If the composite is invalid (e.g., no participants).
         */
        GradleConnection build() throws GradleConnectionException;
    }

    /**
     * Fetches a Set of snapshots of the model of the given type for this composite. This method blocks until the model is available.
     *
     * <p>This method is simply a convenience for calling {@code models(modelType).get()}</p>
     *
     * @param modelType
     * @param <T>
     * @throws GradleConnectionException
     * @throws IllegalStateException
     */
    <T> Set<T> getModels(Class<T> modelType) throws GradleConnectionException, IllegalStateException;

    /**
     * Starts fetching a Set of snapshots of the model of the given type for this composite, passing the result to the given handler when complete. This method returns immediately, and the result is later
     * passed to the given handler's {@link ResultHandler#onComplete(Object)} method after fetching all of the composite's models.
     *
     * <p>If the operation fails, the handler's {@link ResultHandler#onFailure(GradleConnectionException)} method is called with the appropriate exception.
     * See {@link #getModels(Class)} for a description of the various exceptions that the operation may fail with.</p>
     *
     * <p>An operation will fail if there is a problem fetching the model from any of the composite's builds.
     * The handler's {@code onFailure} method will only be called one time with the first failure.</p>
     *
     * <p>This method is simply a convenience for calling {@code models(modelType).get(handler)}</p>
     *
     * @param modelType
     * @param handler
     * @param <T>
     * @throws IllegalStateException
     */
    <T> void getModels(Class<T> modelType, ResultHandler<? super Set<T>> handler) throws IllegalStateException;

    /**
     * Creates a builder which can be used to query the model of the given type for all projects in the composite.
     *
     * <p>The set of projects is "live", so that models from projects added to the overall composite after the builder
     * was been created will appear in the results without recreating the builder.</p>
     *
     * @param modelType
     * @param <T>
     */
    <T> ModelBuilder<Set<T>> models(Class<T> modelType);

    /**
     * Closes this connection. Blocks until any pending operations are complete. Once this method has returned, no more notifications will be delivered by any threads.
     */
    void close();
}
