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
package org.gradle.foundation.ipc.basic;

import org.gradle.foundation.common.ObserverLord;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.IOException;
import java.io.Serializable;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * This launches an application as a separate process then listens for messages
 * from it. This is a rudimentary form of Inter-Process Communication (IPC).
 * You implement the Protocol interface to handle the specifics of the communications.
 * To use this, instantiate it, then call start. When the communications are finished,
 * call requestShutdown().
 * Your protocal can call sendMessage once communication is started to respond to
 * client's messages.
 *
 * @author mhunsicker
 */
public class ProcessLauncherServer {
    public static final int STARTING_PORT = 1800;
    private final Logger logger = Logging.getLogger(ProcessLauncherServer.class);

    private ServerSocket serverSocket = null;
    private boolean isServerRunning = false;
    private boolean hasRequestedShutdown = false;

    private ObjectSocketWrapper clientSocket;
    private Protocol protocol;
    private Thread communicationThread;
    private int port;
    private volatile ExternalProcess externalProcess;

    private ObserverLord<ServerObserver> observerLord = new ObserverLord<ServerObserver>();

    /**
       Implement this to define the behavior of the communication on the server
       side.
    */
    public interface Protocol extends ServerObserver {
        /**
           Gives your protocol a chance to store this server so it can access its
           functions.
        */
        public void initialize(ProcessLauncherServer server);

        /**
           Notification that the connection was accepted by the client.
        */
        public void connectionAccepted();

        /**
           @return true if we should keep the connection alive. False if we should
                   stop communicaiton.
        */
        public boolean continueConnection();

        /**
           Notification that a message has been received.

           @param  message    the message that was received.
        */
        public void messageReceived(MessageObject message);

        /**
           Notification that the client has stopped all communications.
        */
        public void clientCommunicationStopped();

        /**
           fill in the information needed to execute the other process.

           @param  serverPort    the port the server is listening on. The client should
                                 send messages here
           @param  executionInfo an object continain information about what we execute.
        */
        public void getExecutionInfo(int serverPort, ExecutionInfo executionInfo);

        /**
       Notification that a read failure occurred. This really only exists for
       debugging purposes when things go wrong.
        */
        void readFailureOccurred();
    }

    public interface ServerObserver {
        /**
           Notification that the client has shutdown. Note: this can occur before
           communciations has ever started. You SHOULD get this notification before
           receiving serverExited, even if the client fails to launch or locks up.
           @param result   the return code of the client application
           @param output   the standard error and standard output of the client application
        */
        public void clientExited(int result, String output);

        /**
           Notification that the server has shutdown.
        */
        public void serverExited();
    }

    public ProcessLauncherServer(Protocol protocol) {
        this.protocol = protocol;
        protocol.initialize(this);
    }

    public int getPort() {
        return port;
    }

    /**
       Call this to launch the external application and start communications.
       The interaction will be called to get the command line arguments to pass
       to the process.
       @return true if we started, false if not.
    */
    public boolean execute() {
        port = connect();
        if (port == -1)
            return false;

        communicationThread = new Thread(new Runnable() {
            public void run() {
                listenForConnections();
            }
        });

        communicationThread.start();

        launchExternalProcess();

        return true;
    }

    /**
       This attempts to open a free port. We'll search for an open port until we
       find one.
       @return the port we opened or -1 if we couldn't open one.
    */
    private int connect() {
        int port = STARTING_PORT;
        boolean keepSearching = true;
        while (keepSearching) {
            try {
                isServerRunning = false;
                serverSocket = new ServerSocket(port);
                isServerRunning = true;
                keepSearching = false;
            }
            catch (BindException e) {
                //the port is already in use, go try the next one.
                port++;
            }
            catch (IOException e) {
                logger.error("Could not listen on port: " + port, e);
                keepSearching = false;
                port = -1;
            }
        }
        return port;
    }

    /**
       This sits in a loop and listens for connections. Once a connection has
       been made, we'll call another function to process it.
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

                processCommunications();

                clientSocket.close();
            }
            catch (IOException e) {
                consecutiveFailures++;
                if (consecutiveFailures >= 20)  //if we fail too many times, we'll request to shutdown. It's obviously not working. This is an arbitrary number.
                    requestShutdown();

                if (consecutiveFailures > 8)    //the first few usually fail while we're waiting for the process to startup.
                    logger.error("Accept failed (" + consecutiveFailures + ").");
            }
            catch (Throwable t) {  //something really bad happened, shut down
                logger.error("Listening for connections", t);
                requestShutdown();
            }
        }

        isServerRunning = false;

        stop();
        killProcess(); //if the process is still running, shut it down
        notifyServerExited();
    }

    /**
       This is called once a connection is made. We'll listen for messages from
       the client, notifying the protocol of them to do whatever it needs.
    */
    private void processCommunications() {
        boolean hasClientStopped = false;
        int failureCount = 0;
        while (!hasClientStopped && protocol.continueConnection()) {
            Object object = clientSocket.readObject();

            if (object == null) {
                failureCount++;
                protocol.readFailureOccurred();
                if (failureCount == 3) //after 3 failures, assume the client went away.
                {
                    hasClientStopped = true;
                    protocol.clientCommunicationStopped();
                }
            } else {
                failureCount = 0; //reset our failures

                if (object instanceof String)
                    protocol.messageReceived(new MessageObject("?", object.toString(), null));
                else if (object instanceof MessageObject)
                    protocol.messageReceived((MessageObject) object);
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
       Call this to send a message. The protocal and the client must understand
       the message and message type.

       @param  messageType the message type. Whatever the client and server want.
       @param  message     the message being sent.
    */
    public void sendMessage(String messageType, String message) {
        clientSocket.sendObject(new MessageObject(messageType, message, null));
    }

    /**
       Call this to send a message with some binary data. The protocal and the
       client must understand the message, message type, and data.

       @param  messageType the message type. Whatever the client and server want.
       @param  message     the message being sent
       @param  data        the data being sent. Must be serializable.
    */
    public void sendMessage(String messageType, String message, Serializable data) {
        clientSocket.sendObject(new MessageObject(messageType, message, data));
    }

    /**
       This launches an external process in a thread and waits for it to exit.
    */
    private void launchExternalProcess() {
        Thread thread = new Thread(new Runnable() {
            public void run() {
                ExecutionInfo executionInfo = new ExecutionInfo();
                protocol.getExecutionInfo(port, executionInfo);

                ExternalProcess externalProcess = new ExternalProcess(executionInfo.workingDirectory, executionInfo.commandLineArguments);
                setExternalProcess(externalProcess);

                try {
                    externalProcess.start();
                }
                catch (Throwable e) {
                    logger.error("Starting external process", e);
                    protocol.clientExited(-1, e.getMessage());
                    setExternalProcess(null);
                    return;
                }

                int result = 0;
                try {
                    result = externalProcess.waitFor();
                }
                catch (InterruptedException e) {
                    logger.error("Waiting for external process", e);
                }

                setExternalProcess(null);   //clear our external process member variable (we're using our local variable below). This is so we know the process has already stopped.

                protocol.clientExited(result, externalProcess.getOutput());
            }
        });

        thread.start();
    }

    public void stop() {
        try {
            serverSocket.close();
        }
        catch (IOException e) {
            logger.error("Closing socket", e);
        }
    }

    public void setExternalProcess(ExternalProcess externalProcess) {
        this.externalProcess = externalProcess;
    }

    /**
       Call this to violently kill the external process. This is NOT a good way
       to stop it. It is preferrable to ask the thread to stop. However, gradle
       has no way to do that, so we'll be killing it.
    */
    public synchronized void killProcess() {
        if (externalProcess != null) {
            try {
                externalProcess.stop();
            }
            catch (InterruptedException e) {
                logger.error("Stopping external process", e);
                //just keep going. This means something probably went bad with recording the output, but the process should be stopped.
            }

            setExternalProcess(null);
            notifyClientExited(-1, "Process Canceled");
        }
    }

    private void notifyClientExited(final int result, final String output) {
        protocol.clientExited(result, output);

        observerLord.notifyObservers(new ObserverLord.ObserverNotification<ServerObserver>() {
            public void notify(ServerObserver observer) {
                observer.clientExited(result, output);
            }
        });
    }

    private void notifyServerExited() {
        protocol.serverExited();

        observerLord.notifyObservers(new ObserverLord.ObserverNotification<ServerObserver>() {
            public void notify(ServerObserver observer) {
                observer.serverExited();
            }
        });
    }

    public void addServerObserver(ServerObserver observer, boolean inEventQueue) {
        observerLord.addObserver(observer, inEventQueue);
    }

    public void removeServerObserver(ServerObserver observer) {
        observerLord.removeObserver(observer);
    }
}
