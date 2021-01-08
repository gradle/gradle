/*
 * Copyright 2012 the original author or authors.
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
 * Mixed into a provider connection, to run actions against a build.
 *
 * <p>DO NOT CHANGE THIS INTERFACE - it is part of the cross-version protocol.
 *
 * <p>Consumer compatibility: This interface is used by all consumer versions from 1.2-rc-1 to 1.5. It is also used by later consumers when the provider does not
 * implement newer interfaces.
 * </p>
 * <p>Provider compatibility: This interface is implemented by all provider versions from 1.2-rc-1.</p>
 *
 * @since 1.2-rc-1
 * @deprecated 1.6-rc-1. Use {@link InternalCancellableConnection} instead.
 * @see ConnectionVersion4
 */
@Deprecated
public interface BuildActionRunner extends InternalProtocolInterface {
    /**
     * Performs some action against a build and returns some result of the given type.
     *
     * <p>Consumer compatibility: This method is used by all consumer versions from 1.2-rc-1 to 1.5.</p>
     * <p>Provider compatibility: This method is implemented by all provider versions from 1.2-rc-1. Provider versions 3.0 and later fail with a 'no longer supported' exception.</p>
     *
     * @param type The desired result type. Use {@link Void} to indicate that no result is desired.
     * @throws UnsupportedOperationException When the given model type is not supported.
     * @throws IllegalStateException When this connection has been stopped.
     * @since 1.2-rc-1
     * @deprecated 1.6-rc-1. Use {@link InternalCancellableConnection#getModel(ModelIdentifier, InternalCancellationToken, BuildParameters)} instead.
     */
    @Deprecated
    <T> BuildResult<T> run(Class<T> type, BuildParameters operationParameters) throws UnsupportedOperationException, IllegalStateException;
}
