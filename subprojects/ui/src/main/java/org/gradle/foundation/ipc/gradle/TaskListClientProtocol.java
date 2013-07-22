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

import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.foundation.ProjectConverter;
import org.gradle.foundation.ProjectView;
import org.gradle.foundation.ipc.basic.ClientProcess;
import org.gradle.foundation.ipc.basic.MessageObject;
import org.gradle.foundation.ipc.basic.Server;
import org.gradle.gradleplugin.foundation.GradlePluginLord;

import java.io.Serializable;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * This manages the communication between the UI and an externally-launched copy of Gradle when using socket-based inter-process communication. This is the client (gradle) side used to build a task
 * list (tree actually). We add gradle listeners and send their notifications as messages back to the server.
 */
public class TaskListClientProtocol implements ClientProcess.Protocol {
    private final Logger logger = Logging.getLogger(TaskListClientProtocol.class);
    private ClientProcess client;
    private boolean continueConnection = true;
    private Gradle gradle;

    private Server localServer;   //this is our server that will listen to the process that started us.

    public TaskListClientProtocol(Gradle gradle) {
        this.gradle = gradle;
    }

    /**
     * Gives your protocol a chance to store this client so it can access its functions.
     */
    public void initialize(ClientProcess client) {
        this.client = client;
        this.gradle.addListener(new RefreshTaskListBuildListener(client));
    }

    /**
     * Listener used to delegate gradle messages to our listeners.
     */
    private class RefreshTaskListBuildListener extends BuildAdapter implements StandardOutputListener {
        private ClientProcess client;
        private StringBuffer allOutputText = new StringBuffer(); //this is potentially threaded, so use StringBuffer instead of StringBuilder

        public RefreshTaskListBuildListener(ClientProcess client) {
            this.client = client;
        }

        public synchronized void onOutput(CharSequence output) {
            allOutputText.append(output);
        }

        /**
         * <p>Called when the build is completed. All selected tasks have been executed.</p>
         *
         * @param buildResult The result of the build. Never null.
         */
        @Override
        public void buildFinished(BuildResult buildResult) {
            boolean wasSuccessful = buildResult.getFailure() == null;
            String output = allOutputText.toString();

            if (!wasSuccessful) //if we fail, send the results, otherwise, we'll send the projects.
            {
                //we can't send the exception itself because it might not be serializable (it can include anything from anywhere inside gradle
                //or one of its dependencies). So format it as text.
                String details = GradlePluginLord.getGradleExceptionMessage(buildResult.getFailure(), gradle.getStartParameter().getShowStacktrace());
                output += details;

                client.sendMessage(ProtocolConstants.TASK_LIST_COMPLETED_WITH_ERRORS_TYPE, output, wasSuccessful);
            } else {
                ProjectConverter buildExecuter = new ProjectConverter();
                List<ProjectView> projects = new ArrayList<ProjectView>();
                projects.addAll(buildExecuter.convertProjects(buildResult.getGradle().getRootProject()));

                client.sendMessage(ProtocolConstants.TASK_LIST_COMPLETED_SUCCESSFULLY_TYPE, output, (Serializable) projects);
            }

            //tell the server we're going to exit.
            client.sendMessage(ProtocolConstants.EXITING, null, null);

            client.stop();
        }
    }

    /**
     * Notification that we have connected to the server. Do minimum handshaking.
     *
     * @return true if we should continue the connection, false if not.
     */
    public boolean serverConnected(Socket clientSocket) {
        MessageObject message = client.readMessage();
        if (message == null) {
            return false;
        }

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
     * @return true if we should keep the connection alive. False if we should stop communicaiton.
     */
    public boolean continueConnection() {
        return continueConnection;
    }

    public void shutdown() {
        continueConnection = false;
    }
}
