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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.foundation.common.ObserverLord;

import java.io.IOException;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * This is a server that talks to a client via sockets (Rudimentary form of Inter-Process Communication (IPC)). This does the work of locating a free socket and starting the connection. To use this,
 * you really only have to define a Protocol that handles the actual messages. You'll want to make your client startup a ClientProcess object that implements a corresponding Protocol.
 */
public class Server<P extends Server.Protocol, O extends Server.ServerObserver> {
    private final Logger logger = Logging.getLogger(Server.class);

    private ServerSocket serverSocket;
    private boolean isServerRunning;
    private boolean hasRequestedShutdown;

    private ObjectSocketWrapper clientSocket;
    protected P protocol;
    private Thread communicationThread;
    private int port;

    protected ObserverLord<O> observerLord = new ObserverLord<O>();

    //

    /**
     * Implement this to define the behavior of the communication on the server side.
     */
    public interface Protocol<S extends Server> {
        /**
         * Gives your protocol a chance to store this server so it can access its functions.
         */
        public void initialize(S server);

        /**
         * Notification that the connection was accepted by the client.
         */
        public void connectionAccepted();

        /**
         * @return true if we should keep the connection alive. False if we should stop communication.
         */
        public boolean continueConnection();

        /**
         * Notification that a message has been received.
         *
         * @param message the message that was received.
         */
        public void messageReceived(MessageObject message);

        /**
         * Notification that the client has stopped all communications.
         */
        public void clientCommunicationStopped();

        /**
         * Notification that a read failure occurred. This really only exists for debugging purposes when things go wrong.
         */
        void readFailureOccurred();
    }

    //
    public interface ServerObserver {
        /**
         * Notification that the server has shutdown.
         */
        public void serverExited();
    }

    public Server(P protocol) {
        this.protocol = protocol;
        protocol.initialize(this);
    }

    public int getPort() {
        return port;
    }

    /**
     * Call this to start the server.
     *
     * @return true if we started, false if not.
     */
    public boolean start() {
        port = connect();
        if (port == -1) {
            return false;
        }

        communicationThread = new Thread(new Runnable() {
            public void run() {
                listenForConnections();
            }
        });

        communicationThread.start();

        communicationsStarted();

        return true;
    }

    /**
     * this exists solely so it can be overridden. Its an internal notification that communcations have started. You may want to do some extra processing now.
     */

    protected void communicationsStarted() {

    }

    /**
     * This attempts to open a free port. We'll search for an open port until we find one.
     *
     * @return the port we opened or -1 if we couldn't open one.
     */
    private int connect() {
        try {
            serverSocket = new ServerSocket(0);
            return serverSocket.getLocalPort();
        } catch (IOException e) {
            logger.error("Could not listen on port: " + port, e);
            return -1;
        }
    }

    /**
     * This sits in a loop and listens for connections. Once a connection has been made, we'll call another function to process it.
     */
    private void listenForConnections() {
        int consecutiveFailures = 0;
        while (!hasRequestedShutdown) {
            Socket socket = null;
            try {
                serverSocket.setSoTimeout(2000);  //attempt to connect for a few seconds, then try again (so we'll get any shutdown requests).
                socket = serverSocket.accept();

                clientSocket = new ObjectSocketWrapper(socket);
                protocol.connectionAccepted();
                consecutiveFailures = 0;   //reset our consecutive failures.
                serverSocket.setSoTimeout(0);

                processCommunications();

                clientSocket.close();
            } catch (IOException e) {
                consecutiveFailures++;
                if (consecutiveFailures >= 20)  //if we fail too many times, we'll request to shutdown. It's obviously not working. This is an arbitrary number.
                {
                    requestShutdown();
                }

                if (consecutiveFailures > 8)    //the first few usually fail while we're waiting for the process to startup.
                {
                    logger.error("Accept failed (" + consecutiveFailures + ").");
                }
            } catch (Throwable t) {  //something really bad happened, shut down
                logger.error("Listening for connections", t);
                requestShutdown();
            }
        }

        isServerRunning = false;

        stop();
        notifyServerExited();
    }

    /**
     * This is called once a connection is made. We'll listen for messages from the client, notifying the protocol of them to do whatever it needs.
     */
    private void processCommunications() {
        boolean hasClientStopped = false;
        int failureCount = 0;
        while (!hasClientStopped && protocol.continueConnection() && !hasRequestedShutdown) {
            Object object = clientSocket.readObject();

            if (object == null) {
                if (!hasRequestedShutdown)   //if we're trying to shutdown, we can get errors here. Just ignore them and move on
                {
                    failureCount++;
                    protocol.readFailureOccurred();
                    if (failureCount == 3) //after 3 failures, assume the client went away.
                    {
                        hasClientStopped = true;
                        protocol.clientCommunicationStopped();
                    }
                }
            } else {
                failureCount = 0; //reset our failures

                if (object instanceof String) {
                    protocol.messageReceived(new MessageObject("?", object.toString(), null));
                } else if (object instanceof MessageObject) {
                    protocol.messageReceived((MessageObject) object);
                }
            }
        }
    }

    public void requestShutdown() {
        hasRequestedShutdown = true;
    }

    public boolean isServerRunning() {
        return isServerRunning;
    }

    /**
     * Call this to send a message. The protocal and the client must understand the message and message type.
     *
     * @param messageType the message type. Whatever the client and server want.
     * @param message the message being sent.
     */
    public void sendMessage(String messageType, String message) {
        clientSocket.sendObject(new MessageObject(messageType, message, null));
    }

    /**
     * Call this to send a message with some binary data. The protocal and the client must understand the message, message type, and data.
     *
     * @param messageType the message type. Whatever the client and server want.
     * @param message the message being sent
     * @param data the data being sent. Must be serializable.
     */
    public void sendMessage(String messageType, String message, Serializable data) {
        clientSocket.sendObject(new MessageObject(messageType, message, data));
    }

    public void stop() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            logger.error("Closing socket", e);
        }
    }

    private void notifyServerExited() {
        observerLord.notifyObservers(new ObserverLord.ObserverNotification<O>() {
            public void notify(ServerObserver observer) {
                observer.serverExited();
            }
        });
    }

    public void addServerObserver(O observer, boolean inEventQueue) {
        observerLord.addObserver(observer, inEventQueue);
    }

    public void removeServerObserver(O observer) {
        observerLord.removeObserver(observer);
    }
}
