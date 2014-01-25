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

package org.gradle.messaging.remote;

import org.gradle.api.Action;

/**
 * A {@code MessagingServer} allows the creation of multiple bi-directional uni-cast connections.
 */
public interface MessagingServer {
    /**
     * Creates an endpoint that peers can connect to. Assigns an arbitrary address.
     *
     * @param action The action to execute when a connection has been established.
     * @return The local address of the endpoint, for the peer to connect to.
     */
    ConnectionAcceptor accept(Action<ObjectConnection> action);
}
