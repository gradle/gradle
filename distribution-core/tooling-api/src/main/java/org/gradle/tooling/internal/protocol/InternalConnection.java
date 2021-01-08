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
 * <p>DO NOT CHANGE THIS INTERFACE - it is part of the cross-version protocol.
 *
 * <p>Consumer compatibility: This interface is used by all consumer versions from 1.0-milestone-8 to 1.1. It is also used by later consumers when the
 * provider does not implement newer interfaces. It is not used by provider versions 3.0 and later.</p>
 * <p>Provider compatibility: This interface is implemented by all provider versions from 1.0-milestone-8.</p>
 *
 * @since 1.0-milestone-8
 * @deprecated 1.2-rc-1. Use {@link InternalCancellableConnection} instead.
 * @see ConnectionVersion4
 */
@Deprecated
public interface InternalConnection extends ConnectionVersion4, InternalProtocolInterface {
    /**
     * Fetches a snapshot of the model for the project. This method is generic so that we're not locked
     * to building particular model type.
     * <p>
     * The other method on the interface, e.g. {@link #getModel(Class, BuildOperationParametersVersion1)} should be considered deprecated
     *
     * <p>Consumer compatibility: This method is used by all consumer versions from 1.0-milestone-8 to 1.1. It is also used by later consumers when the
     * provider does not implement newer interfaces. It is not used by provider versions 3.0 and later.</p>
     * <p>Provider compatibility: This interface is implemented by all provider versions from 1.0-milestone-8. Provider versions 2.0 and later fail with a 'no longer supported' exception.</p>
     *
     * @throws UnsupportedOperationException When the given model type is not supported.
     * @throws IllegalStateException When this connection has been stopped.
     * @since 1.0-milestone-8
     * @deprecated 1.2-rc-1 Use {@link InternalCancellableConnection#getModel(ModelIdentifier, InternalCancellationToken, BuildParameters)} instead.
     */
    @Deprecated
    <T> T getTheModel(Class<T> type, BuildOperationParametersVersion1 operationParameters) throws UnsupportedOperationException, IllegalStateException;
}
