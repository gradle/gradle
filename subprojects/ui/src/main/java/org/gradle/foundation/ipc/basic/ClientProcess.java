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
package org.gradle.foundation.ipc.basic;

import org.gradle.api.UncheckedIOException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.IOException;
import java.io.Serializable;
import java.net.Socket;

/**
 * The client of what the ProcessLauncherServer launches. The client makes a connection to the server and sends messages to it. The server responds to those messages, but does not initiate
 * communications otherwise. You implement the Protocol interface to handle the specifics of the communications.
 */
public class ClientProcess {
    private final Logger logger = Logging.getLogger(ClientProcess.class);

    /**
     * Implement this to define the behavior of the communication on the client side.
     */
    public interface Protocol {
        /**
         * Gives your protocol a chance to store this client so it can access its functions.
         */
        public void initialize(ClientProcess client);

        /**
         * Notification that we have connected to the server.
         *
         * @return true if we should continue the connection, false if not.
         */
        public boolean serverConnected(Socket clientSocket);

        /**
         * @return true if we should keep the connection alive. False if we should stop communicaiton.
         */
        public boolean continueConnection();
    }

    private ObjectSocketWrapper socketWrapper;
    private Protocol protocol;

    public ClientProcess(Protocol protocol) {
        this.protocol = protocol;
        protocol.initialize(this);
    }

    /**
     * Call this to attempt to connect to the server.
     *
     * @param port where the server is listening. Since it launched this client, it should have either been passed to it on the command line or via a system property (-D).
     */
    public void start(int port) {
        Socket clientSocket;
        try {
            clientSocket = new Socket((String) null, port);
            socketWrapper = new ObjectSocketWrapper(clientSocket);
            if (protocol.serverConnected(clientSocket)) {
                return;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("Could not connect to GUI server at port %s.", port), e);
        }

        try {
            clientSocket.close();
            socketWrapper = null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        throw new UncheckedIOException("GUI protocol failed to connect.");
    }

    /**
     * Call this to stop communications with the server.
     */
    public void stop() {
        if (socketWrapper != null) {
            socketWrapper.close();
        }
    }

    /**
     * Call this to send a message with some binary data. The protocal and the server must understand the message, message type, and data.
     *
     * @param messageType the message type. Whatever the client and server want.
     * @param message the message being sent
     * @param data the data being sent. Must be serializable.
     * @return true if we sent the message, false if not.
     */
    public boolean sendMessage(String messageType, String message, Serializable data) {
        return socketWrapper.sendObject(new MessageObject(messageType, message, data));
    }

    public boolean sendMessage(String messageType, String message) {
        return sendMessage(messageType, message, null);
    }

    /**
     * Call this to send a message with some binary data and wait for the server's acknowledgement. The protocol and the server must understand the message, message type, and data.
     *
     * @param messageType the message type. Whatever the client and server want.
     * @param message the message being sent
     * @param data the data being sent. Must be serializable.
     * @return the reply from the server
     */
    public MessageObject sendMessageWaitForReply(String messageType, String message, Serializable data) {
        if (!socketWrapper.sendObject(new MessageObject(messageType, message, data))) {
            return null;
        }

        return readMessage();
    }

    /**
     * Call this to listen for a message from the server. This is really only meant to be a response from the server as a response to our message.
     *
     * @return the message returned.
     */
    public MessageObject readMessage() {
        Object object = socketWrapper.readObject();
        if (object == null) {
            return null;
        }

        if (object instanceof MessageObject) {
            return (MessageObject) object;
        }

        return new MessageObject("?", object.toString(), null);
    }
}
