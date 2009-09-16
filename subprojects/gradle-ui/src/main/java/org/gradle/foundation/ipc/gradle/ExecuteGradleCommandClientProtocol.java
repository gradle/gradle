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

import org.gradle.BuildResult;
import org.gradle.ExecutionListener;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.foundation.TemporaryExecutionListener;
import org.gradle.foundation.ipc.basic.ClientProcess;
import org.gradle.foundation.ipc.basic.MessageObject;
import org.gradle.foundation.ipc.basic.Server;
import org.gradle.gradleplugin.foundation.GradlePluginLord;

import java.util.Timer;
import java.util.TimerTask;
import java.net.Socket;

/**
 * This manages the communication between the UI and an externally-launched copy
 * of Gradle when using socket-based inter-process communication. This is the
 * client (gradle) side used when executing commands (the most common case). We
 * add gradle listeners and send their notifications as messages back to the server.
 *
 * @author mhunsicker
 */
public class ExecuteGradleCommandClientProtocol implements ClientProcess.Protocol {
    private final Logger logger = Logging.getLogger(ExecuteGradleCommandClientProtocol.class);
    private ClientProcess client;
    private boolean continueConnection = true;
    private Gradle gradle;

    private Server localServer;   //this is our server that will listen to the process that started us.

    public ExecuteGradleCommandClientProtocol(Gradle gradle) {
        this.gradle = gradle;
    }

    /**
     * Gives your protocol a chance to store this client so it can access its
     * functions.
     */
    public void initialize(ClientProcess client) {
        this.client = client;

        TemporaryExecutionListener.addExecutionListener(gradle, new IPCExecutionListener(client));
    }

    /**
     * Notification that we have connected to the server. Do minimum handshaking.
     *
     * @return true if we should continue the connection, false if not.
     */
    public boolean serverConnected(Socket clientSocket) {
        MessageObject message = client.readMessage();
        if (message == null)
            return false;

        if (!ProtocolConstants.HANDSHAKE_TYPE.equalsIgnoreCase(message.getMessageType())) {
            logger.error("Incorrect server handshaking.");
            return false;
        }

        localServer = new Server(new KillGradleServerProtocol());
        localServer.start();

        client.sendMessage(ProtocolConstants.HANDSHAKE_TYPE, ProtocolConstants.HANDSHAKE_CLIENT, localServer.getPort());

        return true;
    }

    /**
     * We just keep a flag around for this.
     *
     * @return true if we should keep the connection alive. False if we should
     *         stop communicaiton.
     */
    public boolean continueConnection() {
        return continueConnection;
    }

    public void shutdown() {
        continueConnection = false;
    }


    /**
     * This converts gradle messages to messages that we send to our server over
     * a socket.
     *
     */
    private class IPCExecutionListener implements ExecutionListener {
        private ClientProcess client;
        StringBuffer bufferedLiveOutput = new StringBuffer();
        Timer liveOutputTimer;

        public IPCExecutionListener(ClientProcess client) {
            this.client = client;

            liveOutputTimer = new Timer();
            liveOutputTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    sendLiveOutput();
                }
            }, 500, 500);
        }


        public void reportExecutionStarted() {
            //we'll never get this message because execution has started before we're instantiated
        }

        public void reportTaskStarted(String currentTaskName, float percentComplete) {
            client.sendMessage(ProtocolConstants.TASK_STARTED_TYPE, currentTaskName, new Float(percentComplete));
        }

        public void reportTaskComplete(String currentTaskName, float percentComplete) {
            client.sendMessage(ProtocolConstants.TASK_COMPLETE_TYPE, currentTaskName, new Float(percentComplete));
        }

        public synchronized void reportLiveOutput(String output) {
            //we'll buffer our live output so we're not sending it constantly.
            this.bufferedLiveOutput.append(output);
        }

        private synchronized void sendLiveOutput() {
            if (bufferedLiveOutput.length() == 0) {
                return;  //nothing to send
            }

            String text = bufferedLiveOutput.toString();
            bufferedLiveOutput = new StringBuffer();

            client.sendMessage(ProtocolConstants.LIVE_OUTPUT_TYPE, text);
        }

        public void reportExecutionFinished(boolean wasSuccessful, BuildResult buildResult, String output) {
            liveOutputTimer.cancel();  //stop our timer and send whatever live output we have
            sendLiveOutput();

            //because we're going to send two messages in row, we need to wait for the reply or we'll get a broken pipe exception
            //when the server tries to reply with an acknowedgement.

            //we can't send the exception itself because it might not be serializable (it can include anything from anywhere inside gradle
            //or one of its dependencies). So format it as text.
            String details = GradlePluginLord.getGradleExceptionMessage(buildResult.getFailure(), gradle.getStartParameter().getShowStacktrace());
            output += details;

            client.sendMessageWaitForReply(ProtocolConstants.EXECUTION_COMPLETED_TYPE, output, new Boolean(wasSuccessful));

            client.sendMessageWaitForReply(ProtocolConstants.EXITING, null, null);
            client.stop();
        }
    }
}

