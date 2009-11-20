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

import org.apache.mina.transport.vmpipe.VmPipeAddress;
import org.apache.mina.transport.vmpipe.VmPipeConnector;

import java.net.SocketAddress;

/**
 * In JVM 'network' communication connector. Uses the Apache MINA VmPipeConnector.
 *
 * @author Tom Eyckmans
 */
public class InternalIoConnectorFactory extends AbstractIoConnectorFactory<VmPipeConnector> {
    /**
     * Creates a 'network' communication connector with the selected port.
     *
     * @param port The 'network' port the communication connector needs to connect to.
     */
    public InternalIoConnectorFactory(int port) {
        super(port);
    }

    /**
     * Creates a new instance of a VmPipeConnector.
     *
     * @return The newly instanciated VmPipeConnector instance.
     */
    protected VmPipeConnector instanciateIoConnector() {
        return new VmPipeConnector();
    }

    /**
     * Create an instance of a VmPipeAddress.
     *
     * @return The created VmPipeAddress.
     */
    public SocketAddress getSocketAddress() {
        return new VmPipeAddress(port);
    }
}
