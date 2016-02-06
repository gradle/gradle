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
import org.gradle.tooling.*;

import java.util.Set;

/**
 * A {@code CompositeModelBuilder} allows you to fetch a set of snapshots of some model for all projects in a composite.
 * Instances of {@code CompositeModelBuilder} are not thread-safe.
 * <p>
 * You use a {@code CompositeModelBuilder} as follows:
 *
 * <ul>
 * <li>Create an instance of {@code CompositeModelBuilder} by calling {@link org.gradle.tooling.composite.GradleConnection#models(Class)}.
 * <li>Configure the builder as appropriate.
 * <li>Call either {@link #get()} or {@link #get(ResultHandler)} to build the model.
 * <li>Optionally, you can reuse the builder to build the model multiple times.
 * </ul>
 *
 * Example:
 * <pre autoTested=''>
 *
 * GradleConnection connection = GradleConnector.newGradleConnectionBuilder()
 *    .addBuild(new File("someFolder"))
 *    .build();
 *
 * try {
 *    CompositeModelBuilder&lt;GradleProject&gt; builder = connection.models(GradleProject.class);
 *
 *    //get the model:
 *    java.util.Set&lt;GradleProject&gt; projects = builder.get();
 *
 *    //query the model for information:
 *    for (GradleProject project : projects) {
 *       System.out.println("Available tasks: " + project.getTasks());
 *    }
 * } finally {
 *    connection.close();
 * }
 * </pre>
 *
 * @param <T> The type of model to build
 * @since 2.12
 */
@Incubating
public interface CompositeModelBuilder<T> {

    /**
     * Fetch the model, blocking until it is available.
     *
     * @return The model.
     * @throws UnsupportedVersionException When the target Gradle version does not support building models.
     * @throws UnknownModelException When the target Gradle version or build does not support the requested model.
     * @throws BuildException On some failure executing the Gradle build.
     * @throws BuildCancelledException When the operation was cancelled before it completed successfully.
     * @throws GradleConnectionException On some other failure using the connection.
     * @throws IllegalStateException When the connection has been closed or is closing.
     */
    Set<T> get() throws GradleConnectionException, IllegalStateException;

    /**
     * Starts fetching the model, passing the result to the given handler when complete. This method returns immediately, and the result is later passed to the given
     * handler's {@link ResultHandler#onComplete(Object)} method.
     *
     * <p>If the operation fails, the handler's {@link ResultHandler#onFailure(GradleConnectionException)}
     * method is called with the appropriate exception. See {@link #get()} for a description of the various exceptions that the operation may fail with.</p>
     *
     * <p>Either {@code onComplete} or {@code onFailure} are called depending on the overall result. {@code onFailure} is only called once, regardless of the number of failures.</p>
     *
     * @param handler The handler to supply the result to.
     * @throws IllegalStateException When the connection has been closed or is closing.
     */
    void get(ResultHandler<? super Set<T>> handler) throws IllegalStateException;

    /**
     * Sets the cancellation token to use to cancel the operation if required.
     *
     * <p>Supported by participant builds with Gradle 2.1 or later. Ignored for older versions.</p>
     *
     */
    @Incubating
    CompositeModelBuilder<T> withCancellationToken(CancellationToken cancellationToken);
}
