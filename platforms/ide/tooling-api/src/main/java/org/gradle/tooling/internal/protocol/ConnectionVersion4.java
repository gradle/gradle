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
 * <li>Implementations should implement {@link InternalInvalidatableVirtualFileSystemConnection}. This is used by all consumer versions from 6.1.
 * <li>Implementations should implement {@link org.gradle.tooling.internal.protocol.test.InternalTestExecutionConnection}. This is used by all consumer versions from 2.6-rc-1.
 * <li>Implementations should implement {@link InternalPhasedActionConnection}. This is used by all consumer versions from 4.8.
 * <li>Implementations should implement {@link InternalParameterAcceptingConnection}. This is used by all consumer versions from 4.4.
 * <li>Implementations should implement {@link InternalCancellableConnection#getModel(ModelIdentifier, InternalCancellationToken, BuildParameters)}.
 * This is used by all consumer versions from 2.1-rc-1.
 * </li>
 * <li>Implementations should implement {@link ConfigurableConnection}. This is used by all consumer versions from 1.2-rc-1.
 * <li>Implementations should implement {@link StoppableConnection}. This is used by all consumer versions from 2.2-rc-1.
 * <li>Implementations should provide a zero-args constructor. This is used by all consumer versions from 1.0-milestone-3.
 * <li>For backwards compatibility, implementations should implement {@link InternalCancellableConnection#run(InternalBuildAction, InternalCancellationToken, BuildParameters)}.
 * This is used by all consumer versions from 2.1-rc-1 to 4.3.
 * </li>
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
     * <p>Returns the meta-data for this connection. The implementation of this method should be fast, and should continue to work after the connection has been stopped.
     *
     * <p>Consumer compatibility: This method is used by all consumer versions from 1.0-milestone-3.</p>
     * <p>Provider compatibility: This method is implemented by all provider versions from 1.0-milestone-3.</p>
     *
     * @return The meta-data.
     * @since 1.0-milestone-3
     */
    ConnectionMetaDataVersion1 getMetaData();
}
