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
 * Mixed into a provider connection, to allow the connection to be configured.
 *
 * <p>DO NOT CHANGE THIS INTERFACE - it is part of the cross-version protocol.
 *
 * <p>Consumer compatibility: This interface is used by all consumer versions from 1.2-rc-1.</p>
 * <p>Provider compatibility: This interface is implemented by all provider versions from 1.2-rc-1.</p>
 *
 * @since 1.2-rc-1
 * @see ConnectionVersion4
 */
public interface ConfigurableConnection extends InternalProtocolInterface {
    /**
     * Configures this connection with the given parameters.
     *
     * <p>Consumer compatibility: This method is used by all consumer versions from 1.2-rc-1.</p>
     * <p>Provider compatibility: This method is implemented by all provider versions from 1.2-rc-1.</p>
     *
     * @since 1.2-rc-1
     */
    void configure(ConnectionParameters parameters);
}
