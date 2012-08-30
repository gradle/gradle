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
 * @since 1.2-rc-1
 */
public interface BuildActionRunner extends InternalProtocolInterface {
    /**
     * Performs some action against a build and returns some result of the given type.
     *
     * @param type The desired result type. Use {@link Void} to indicate that no result is desired.
     * @throws UnsupportedOperationException When the given model type is not supported.
     * @throws IllegalStateException When this connection has been stopped.
     */
    <T> BuildResult<T> run(Class<T> type, BuildParameters operationParameters) throws UnsupportedOperationException, IllegalStateException;
}
