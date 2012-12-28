/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.messaging.remote.Address;

public interface OutgoingConnector {
    /**
     * Creates a connection to the given address. Uses default Java serialization for messages.
     *
     * @param messageClassLoader ClassLoader to use to load incoming messages.
     * @throws ConnectException when there is nothing listening on the remote address.
     */
    <T> Connection<T> connect(Address destinationAddress, ClassLoader messageClassLoader) throws ConnectException;

    /**
     * Creates a connection to the given address. Uses the given serializer to convert messages between binary form and objects of type T.
     *
     * @throws ConnectException when there is nothing listening on the remote address
     */
    <T> Connection<T> connect(Address destinationAddress, MessageSerializer<T> serializer) throws ConnectException;
}
