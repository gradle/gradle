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

import org.gradle.tooling.model.Build;

/**
 * Represents a long-lived connection to a Gradle build.
 *
 * <h2>Thread safety</h2>
 *
 * <p>All implementations of {@code GradleConnection} are thread-safe, and may be shared by any number of threads.</p>
 *
 * <p>All notifications from a given {@code GradleConnection} instance are delivered by a single thread at a time. Note however, that the thread may change over time.</p>
 */
public interface GradleConnection {
    /**
     * Fetches a snapshot of the model for this build. This method blocks until the model is available.
     *
     * @param viewType The model type.
     * @param <T> The model type.
     * @return The model.
     * @throws UnsupportedVersionException When the target Gradle version does not support the given model.
     * @throws GradleConnectionException On some failure to communicate with Gradle.
     */
    <T extends Build> T getModel(Class<T> viewType) throws GradleConnectionException;

    /**
     * Fetches a snapshot of the model for this build asynchronously. This method return immediately, and the result of the operation is passed to the supplied result handler.
     *
     * @param viewType The model type.
     * @param handler The handler to pass the result to.
     * @param <T> The model type.
     */
    <T extends Build> void getModel(Class<T> viewType, ResultHandler<? super T> handler);
}
