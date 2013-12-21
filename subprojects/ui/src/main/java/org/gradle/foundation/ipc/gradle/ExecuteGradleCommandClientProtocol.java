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

import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.api.tasks.TaskState;
import org.gradle.foundation.ipc.basic.ClientProcess;
import org.gradle.foundation.ipc.basic.MessageObject;
import org.gradle.foundation.ipc.basic.Server;
import org.gradle.gradleplugin.foundation.GradlePluginLord;

import java.net.Socket;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * This manages the communication between the UI and an externally-launched copy of Gradle when using socket-based inter-process communication. This is the client (gradle) side used when executing
 * commands (the most common case). We add gradle listeners and send their notifications as messages back to the server.
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
     * Gives your protocol a chance to store this client so it can access its functions.
     */
    public void initialize(ClientProcess client) {
        this.client = client;

        gradle.addListener(new IPCExecutionListener(client));
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

    /**
     * This converts gradle messages to messages that we send to our server over a socket. It also tracks the live output and periodically sends it to the server.
     */
    private class IPCExecutionListener implements BuildListener, StandardOutputListener, TaskExecutionGraphListener, TaskExecutionListener {
        private ClientProcess client;

        private StringBuffer allOutputText = new StringBuffer(); //this is potentially threaded, so use StringBuffer instead of StringBuilder
        private StringBuffer bufferedLiveOutput = new StringBuffer();
        private Timer liveOutputTimer;
        private float totalTasksToExecute;
        private float totalTasksExecuted;
        private float percentComplete;

        public IPCExecutionListener(ClientProcess client) {
            this.client = client;

            //start a timer to periodically send our live output to our server
            liveOutputTimer = new Timer();
            liveOutputTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    sendLiveOutput();
                }
            }, 500, 500);
        }

        public void buildStarted(Gradle build) {
            //we'll never get this message because execution has started before we were instantiated (and before we were able to add our listener).
        }

        public void graphPopulated(TaskExecutionGraph taskExecutionGraph) {
            List<Task> taskList = taskExecutionGraph.getAllTasks();

            this.totalTasksToExecute = taskList.size();
            client.sendMessage(ProtocolConstants.NUMBER_OF_TASKS_TO_EXECUTE, null, new Integer(taskList.size()));
        }

        /**
         * <p>Called when the build settings have been loaded and evaluated. The settings object is fully configured and is ready to use to load the build projects.</p>
         *
         * @param settings The settings. Never null.
         */
        public void settingsEvaluated(Settings settings) {
            //we don't really care
        }

        /**
         * <p>Called when the projects for the build have been created from the settings. None of the projects have been evaluated.</p>
         *
         * @param gradle The build which has been loaded. Never null.
         */
        public void projectsLoaded(Gradle gradle) {
            //we don't really care
        }

        /**
         * <p>Called when all projects for the build have been evaluated. The project objects are fully configured and are ready to use to populate the task graph.</p>
         *
         * @param gradle The build which has been evaluated. Never null.
         */
        public void projectsEvaluated(Gradle gradle) {
            //we don't really care
        }

        public void beforeExecute(Task task) {
            String currentTaskName = task.getProject().getName() + ":" + task.getName();
            client.sendMessage(ProtocolConstants.TASK_STARTED_TYPE, currentTaskName, new Float(percentComplete));
        }

        public void afterExecute(Task task, TaskState state) {
            totalTasksExecuted++;
            percentComplete = (totalTasksExecuted / totalTasksToExecute) * 100;
            String currentTaskName = task.getProject().getName() + ":" + task.getName();
            client.sendMessage(ProtocolConstants.TASK_COMPLETE_TYPE, currentTaskName, new Float(percentComplete));
        }

        /**
         * Called when some output is written by the logging system.
         *
         * @param output The text.
         */
        public synchronized void onOutput(CharSequence output) {
            this.allOutputText.append(output);
            this.bufferedLiveOutput.append(output);
        }

        /**
         * Called on a timer to send the live output to the process that started us. We only send whatever is there since we've last sent output. It was causing some socket problems to send the output
         * immediately upon receiving it. I suspect due to numerous threads adding output. So its now only done periodically and in a more thread-safe manner.
         */
        private synchronized void sendLiveOutput() {
            if (bufferedLiveOutput.length() == 0) {
                return;  //nothing to send
            }
            String text = bufferedLiveOutput.toString();
            bufferedLiveOutput = new StringBuffer();

            client.sendMessage(ProtocolConstants.LIVE_OUTPUT_TYPE, text);
        }

        /**
         * <p>Called when the build is completed. All selected tasks have been executed.</p> <p>We remove our Log4JAppender as well as our task execution listener. Lastly, we report the build
         * results.</p>
         */
        public void buildFinished(BuildResult buildResult) {

            boolean wasSuccessful = buildResult.getFailure() == null;
            String output = allOutputText.toString();
            liveOutputTimer.cancel();  //stop our timer and send whatever live output we have
            sendLiveOutput();

            //we can't send the exception itself because it might not be serializable (it can include anything from anywhere inside gradle
            //or one of its dependencies). So format it as text.
            String details = GradlePluginLord.getGradleExceptionMessage(buildResult.getFailure(), gradle.getStartParameter().getShowStacktrace());
            output += details;

            client.sendMessage(ProtocolConstants.EXECUTION_COMPLETED_TYPE, output, wasSuccessful);

            client.sendMessage(ProtocolConstants.EXITING, null, null);
            client.stop();
        }
    }
}

