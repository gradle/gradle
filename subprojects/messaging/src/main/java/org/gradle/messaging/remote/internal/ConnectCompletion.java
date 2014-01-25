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

package org.gradle.messaging.remote.internal;

/**
 * A builder that allows a {@link Connection} to be created once the underlying transport with the peer has been
 * established.
 */
public interface ConnectCompletion {
    /**
     * Creates the connection. Uses Java serialization for all messages.
     *
     * @param messageClassLoader The ClassLoader to use to deserialize incoming messages.
     */
    <T> Connection<T> create(ClassLoader messageClassLoader);

    /**
     * Creates the connection. Uses the specified serializer for all messages.
     *
     * @return The serializer to use.
     */
    <T> Connection<T> create(MessageSerializer<T> serializer);
}
