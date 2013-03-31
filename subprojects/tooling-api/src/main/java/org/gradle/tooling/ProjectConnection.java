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

/**
 * Represents a long-lived connection to a Gradle project. You obtain an instance of a {@code ProjectConnection} by using {@link org.gradle.tooling.GradleConnector#connect()}.
 *
 * <pre autoTested=''>
 * ProjectConnection connection = GradleConnector.newConnector()
 *    .forProjectDirectory(new File("someFolder"))
 *    .connect();
 *
 * try {
 *    //obtain some information from the build
 *    BuildEnvironment environment = connection.model(BuildEnvironment.class)
 *      .get();
 *
 *    //run some tasks
 *    connection.newBuild()
 *      .forTasks("tasks")
 *      .setStandardOutput(System.out)
 *      .run();
 *
 * } finally {
 *    connection.close();
 * }
 * </pre>
 *
 * <h3>Thread safety information</h3>
 *
 * <p>All implementations of {@code ProjectConnection} are thread-safe, and may be shared by any number of threads.</p>
 *
 * <p>All notifications from a given {@code ProjectConnection} instance are delivered by a single thread at a time. Note, however, that the delivery thread may change over time.</p>
 * @since 1.0-milestone-3
 */
public interface ProjectConnection {
    /**
     * Fetches a snapshot of the model of the given type for this project. This method blocks until the model is available.
     *
     * <p>This method is simply a convenience for calling {@code model(modelType).get()}</p>
     *
     * @param modelType The model type.
     * @param <T> The model type.
     * @return The model.
     * @throws UnsupportedVersionException When the target Gradle version does not support the given model.
     * @throws UnknownModelException When the target Gradle version or build does not support the requested model.
     * @throws BuildException On some failure executing the Gradle build, in order to build the model.
     * @throws GradleConnectionException On some other failure using the connection.
     * @throws IllegalStateException When this connection has been closed or is closing.
     * @since 1.0-milestone-3
     */
    <T> T getModel(Class<T> modelType) throws GradleConnectionException, IllegalStateException;

    /**
     * Starts fetching a snapshot of the given model, passing the result to the given handler when complete. This method returns immediately, and the result is later
     * passed to the given handler's {@link ResultHandler#onComplete(Object)} method. If the operation fails, the handler's
     * {@link ResultHandler#onFailure(GradleConnectionException)} method is called with the appropriate exception.
     * See {@link #getModel(Class)} for a description of the various exceptions that the operation may fail with.
     *
     * <p>This method is simply a convenience for calling {@code model(modelType).get(handler)}</p>
     *
     * @param modelType The model type.
     * @param handler The handler to pass the result to.
     * @param <T> The model type.
     * @throws IllegalStateException When this connection has been closed or is closing.
     * @since 1.0-milestone-3
     */
    <T> void getModel(Class<T> modelType, ResultHandler<? super T> handler) throws IllegalStateException;

    /**
     * Creates a launcher which can be used to execute a build.
     *
     * @return The launcher.
     * @since 1.0-milestone-3
     */
    BuildLauncher newBuild();

    /**
     * Creates a builder which can be used to build the model of the given type.
     *
     * @param modelType The model type
     * @param <T> The model type.
     * @return The builder.
     * @since 1.0-milestone-3
     */
    <T> ModelBuilder<T> model(Class<T> modelType);

    /**
     * Closes this connection. Blocks until any pending operations are complete. Once this method has returned, no more notifications will be delivered by any threads.
     * @since 1.0-milestone-3
     */
    void close();
}
