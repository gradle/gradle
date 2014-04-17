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
package org.gradle.foundation.ipc.gradle;

import org.gradle.foundation.ipc.basic.ClientProcess;
import org.gradle.internal.UncheckedException;

import java.io.Serializable;

/**
 * <p>This is used to send information from a one process to another process. This one is used by the launched process where the server (the process that launched us) is listening for our messages
 * over a socket connection. The server typically sets the port to listen to via java system properties.</p> <p>To use this, instantiate it, then call start passing in a protocol (which defines the
 * actual communication messages).</p>
 */
public class GradleClient {
    private ClientProcess clientProcess;

    /**
     * Call this to start the client. This version gets the port number as a system property. It does nothing if this property isn't defined.
     *
     * @param protocol the protocol to use to communicate with the server.
     */
    public void start(ClientProcess.Protocol protocol) {
        //make sure we've been given the port number to use
        String portText = System.getProperty(ProtocolConstants.PORT_NUMBER_SYSTEM_PROPERTY);
        if (portText == null) {
            throw new RuntimeException("No port number specified. Cannot run client");
        }

        try {
            int port = Integer.parseInt(portText);
            start(protocol, port);
        } catch (NumberFormatException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    /**
     * Call this to start the client.
     *
     * @param protocol the protocol to use to communicate with the server.
     * @param port the port the server is listening on
     */
    public void start(ClientProcess.Protocol protocol, int port) {
        clientProcess = new ClientProcess(protocol);
        clientProcess.start(port);
    }

    /**
     * Call this to send a message and wait for the server to acknowledge siad message.
     */
    public boolean sendMessage(String messageType, String message, Serializable data) {
        return clientProcess.sendMessage(messageType, message, data);
    }

    public boolean sendMessage(String messageType, String message) {
        return sendMessage(messageType, message, null);
    }

    /**
     * Call this to stop communications with the server.
     */
    public void stop() {
        clientProcess.stop();
    }
}

