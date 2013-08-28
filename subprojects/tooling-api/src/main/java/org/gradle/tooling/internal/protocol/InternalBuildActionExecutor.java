/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.tooling.internal.protocol.exceptions.InternalUnsupportedBuildArgumentException;

/**
 * Mixed into a provider connection, to allow client-provided build actions to be executed.
 *
 * <p>DO NOT CHANGE THIS INTERFACE - it is part of the cross-version protocol.
 *
 * <p>Consumer compatibility: This interface is used by all consumer versions from 1.8-rc-1.</p>
 * <p>Provider compatibility: This interface is implemented by all provider versions from 1.8-rc-1.</p>
 *
 * @since 1.8-rc-1
 * @see ConnectionVersion4
 */
public interface InternalBuildActionExecutor extends InternalProtocolInterface {
    /**
     * Performs some action against a build and returns the result.
     *
     * <p>Consumer compatibility: This method is used by all consumer versions from 1.8-rc-1.</p>
     * <p>Provider compatibility: This method is implemented by all provider versions from 1.8-rc-1.</p>
     *
     * @throws BuildExceptionVersion1 On build failure.
     * @throws InternalUnsupportedBuildArgumentException When the specified command-line options are not supported.
     * @throws InternalBuildActionFailureException When the action fails with an exception.
     * @throws IllegalStateException When this connection has been stopped.
     * @since 1.8-rc-1
     */
    <T> BuildResult<T> run(InternalBuildAction<T> action,
                           BuildParameters operationParameters) throws
            BuildExceptionVersion1,
            InternalUnsupportedBuildArgumentException,
            InternalBuildActionFailureException,
            IllegalStateException;
}
