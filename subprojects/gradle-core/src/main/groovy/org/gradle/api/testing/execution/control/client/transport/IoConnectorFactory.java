/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.testing.execution.control.client.transport;

import org.apache.mina.core.service.IoConnector;
import org.apache.mina.core.service.IoHandler;

import java.io.IOException;
import java.net.SocketAddress;

/**
 * Base interface for 'network' communication connector factory (client-side).
 *
 * @author Tom Eyckmans
 */
public interface IoConnectorFactory<T extends IoConnector> {
    /**
     * Create and prepare the 'network' communication connector for use.
     *
     * @param handler The handler that needs to be called when messages are received on the 'network' communication
     * connector. Can't be null.
     * @return The prepared 'network' communication connector.
     * @throws IOException When the 'network' communication connector could not be created/prepared.
     */
    T getIoConnector(IoHandler handler) throws IOException;

    /**
     * Retrieve the socket address used by the 'network' communication connector.
     *
     * @return The socket address used by the 'network' communication connector.
     */
    SocketAddress getSocketAddress();
}
