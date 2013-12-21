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
 * <p>Represents a connection to a Gradle implementation.
 *
 * <p>The following constraints apply to implementations:
 * <ul>
 * <li>Implementations must be thread-safe.
 * <li>Implementations should implement {@link ModelBuilder}. This is used by all consumer versions from 1.6-rc-1.
 * <li>Implementations should implement {@link InternalBuildActionExecutor}. This is used by all consumer versions from 1.8-rc-1.
 * <li>Implementations should implement {@link ConfigurableConnection}. This is used by all consumer versions from 1.2-rc-1.
 * <li>Implementations should provide a zero-args constructor. This is used by all consumer versions from 1.0-milestone-3.
 * <li>For backwards compatibility, implementations should implement {@link BuildActionRunner}. This is used by consumer versions from 1.2-rc-1 to 1.5.
 * <li>For backwards compatibility, implementations should implement {@link InternalConnection}. This is used by consumer versions from 1.0-milestone-8 to 1.1.
 * <li>For backwards compatibility, implementations should provide a {@code void configureLogging(boolean verboseLogging)} method. This is used by consumer versions
 * 1.0-rc-1 to 1.1.
 * </ul>
 *
 * <p>DO NOT CHANGE THIS INTERFACE - it is part of the cross-version protocol.
 *
 * <p>Consumer compatibility: This interface is used by all consumer versions from 1.0-milestone-3.</p>
 * <p>Provider compatibility: This interface is implemented by all provider versions from 1.0-milestone-3.</p>
 *
 * @since 1.0-milestone-3
 */
public interface ConnectionVersion4 {
    /**
     * <p>Stops this connection, blocking until complete.
     *
     * <p>Consumer compatibility: This method is used by all consumer versions from 1.0-milestone-3.</p>
     * <p>Provider compatibility: This method is implemented by all provider versions from 1.0-milestone-3.</p>
     *
     * @since 1.0-milestone-3
     */
    void stop();

    /**
     * <p>Returns the meta-data for this connection. The implementation of this method should be fast, and should continue to work after the connection has been stopped.
     *
     * <p>Consumer compatibility: This method is used by all consumer versions from 1.0-milestone-3.</p>
     * <p>Provider compatibility: This method is implemented by all provider versions from 1.0-milestone-3.</p>
     *
     * @return The meta-data.
     * @since 1.0-milestone-3
     */
    ConnectionMetaDataVersion1 getMetaData();

    /**
     * <p>Fetches a snapshot of the model for the project.
     *
     * <p>Consumer compatibility: This method is used by all consumer versions from 1.0-milestone-3 to 1.0-milestone-7. It is also used by later consumers when the provider
     * does not implement {@link InternalConnection} or {@link BuildActionRunner}
     * </p>
     * <p>Provider compatibility: This method is implemented by all provider versions from 1.0-milestone-3.</p>
     *
     * @throws UnsupportedOperationException When the given model type is not supported.
     * @throws IllegalStateException When this connection has been stopped.
     * @since 1.0-milestone-3
     * @deprecated 1.0-milestone-8. Use {@link ModelBuilder#getModel(ModelIdentifier, BuildParameters)} instead.
     */
    @Deprecated
    ProjectVersion3 getModel(Class<? extends ProjectVersion3> type, BuildOperationParametersVersion1 operationParameters) throws UnsupportedOperationException, IllegalStateException;

    /**
     * <p>Executes a build.
     *
     * <p>Consumer compatibility: This method is used by all consumer versions from 1.0-milestone-3 to 1.1. It is also used by later consumers when the provider
     * does not implement {@link BuildActionRunner}
     * </p>
     * <p>Provider compatibility: This method is implemented by all provider versions from 1.0-milestone-3.</p>
     *
     * @param buildParameters The parameters for the build.
     * @throws IllegalStateException When this connection has been stopped.
     * @since 1.0-milestone-3
     * @deprecated 1.2-rc-1. Use {@link ModelBuilder#getModel(ModelIdentifier, BuildParameters)} instead.
     */
    @Deprecated
    void executeBuild(BuildParametersVersion1 buildParameters, BuildOperationParametersVersion1 operationParameters) throws IllegalStateException;
}
