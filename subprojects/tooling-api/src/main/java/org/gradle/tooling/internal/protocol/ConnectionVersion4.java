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
package org.gradle.tooling.internal.protocol;

/**
 * <p>Represents a connection to a particular Gradle implementation.
 *
 * <p>Implementations must be thread-safe.
 *
 * <p>DO NOT CHANGE THIS INTERFACE. It is part of the cross-version protocol.</p>
 */
public interface ConnectionVersion4 {
    /**
     * Stops this connection, blocking until complete.
     */
    void stop();

    /**
     * Returns the meta-data for this connection. The implementation of this method should be fast, and should continue to work after the connection has been stopped.
     * @return The meta-data.
     */
    ConnectionMetaDataVersion1 getMetaData();

    /**
     * Fetches a snapshot of the model for the project.
     *
     * @throws UnsupportedOperationException When the given model type is not supported.
     * @throws IllegalStateException When this connection has been stopped.
     */
    ProjectVersion3 getModel(Class<? extends ProjectVersion3> type, BuildOperationParametersVersion1 operationParameters) throws UnsupportedOperationException, IllegalStateException;

    /**
     * Executes a build.
     *
     * @param buildParameters The parameters for the build.
     * @throws IllegalStateException When this connection has been stopped.
     */
    void executeBuild(BuildParametersVersion1 buildParameters, BuildOperationParametersVersion1 operationParameters) throws IllegalStateException;
}
