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

import org.gradle.api.logging.LogLevel;
import org.gradle.foundation.ipc.basic.MessageObject;
import org.gradle.logging.ShowStacktrace;

import java.io.File;

/**
 * This manages the communication between the UI and an externally-launched copy of Gradle when using socket-based inter-process communication. This is the server side for executing a gradle command.
 * This listens for messages from the gradle client.
 */
public class ExecuteGradleCommandServerProtocol extends AbstractGradleServerProtocol {
    private static final String INIT_SCRIPT_NAME = "execute-command-init-script";

    private ExecutionInteraction executionInteraction;

    public interface ExecutionInteraction {
        /**
         * Notification that gradle has started execution. This may not get called if some error occurs that prevents gradle from running.
         */
        void reportExecutionStarted();

        /**
         * Notification of the total number of tasks that will be executed. This is called after reportExecutionStarted and before any tasks are executed.
         *
         * @param size the total number of tasks.
         */
        void reportNumberOfTasksToExecute(int size);

        /**
         * Notification that execution has finished. Note: if the client fails to launch at all, this should still be called.
         *
         * @param wasSuccessful true if gradle was successful (returned 0)
         * @param message the output of gradle if it ran. If it didn't, an error message.
         * @param throwable an exception if one occurred
         */
        void reportExecutionFinished(boolean wasSuccessful, String message, Throwable throwable);

        void reportTaskStarted(String message, float percentComplete);

        void reportTaskComplete(String message, float percentComplete);

        void reportLiveOutput(String message);
    }

    public ExecuteGradleCommandServerProtocol(File currentDirectory, File gradleHomeDirectory, File customGradleExecutor, String fullCommandLine, LogLevel logLevel,
                                              ShowStacktrace stackTraceLevel, ExecutionInteraction executionInteraction) {
        super(currentDirectory, gradleHomeDirectory, customGradleExecutor, fullCommandLine, logLevel, stackTraceLevel);
        this.executionInteraction = executionInteraction;
    }

    /**
     * Notification that a message was received that we didn't process. Implement this to handle the specifics of your protocol. Basically, the base class handles the handshake. The rest of the
     * conversation is up to you.
     *
     * @param message the message we received.
     */
    @Override
    protected boolean handleMessageReceived(MessageObject message) {
        if (ProtocolConstants.EXECUTION_COMPLETED_TYPE.equals(message.getMessageType())) {
            setHasReceivedBuildCompleteNotification();
            return true;
        }

        if (ProtocolConstants.TASK_STARTED_TYPE.equals(message.getMessageType())) {
            Float percentComplete = (Float) message.getData();
            executionInteraction.reportTaskStarted(message.getMessage(), percentComplete);
            return true;
        }

        if (ProtocolConstants.TASK_COMPLETE_TYPE.equals(message.getMessageType())) {
            Float percentComplete = (Float) message.getData();
            executionInteraction.reportTaskComplete(message.getMessage(), percentComplete);
            return true;
        }

        if (ProtocolConstants.LIVE_OUTPUT_TYPE.equals(message.getMessageType())) {
            executionInteraction.reportLiveOutput(message.getMessage());
            return true;
        }

        if (ProtocolConstants.NUMBER_OF_TASKS_TO_EXECUTE.equals(message.getMessageType())) {
            Integer total = (Integer) message.getData();
            executionInteraction.reportNumberOfTasksToExecute(total.intValue());
        }

        if (ProtocolConstants.EXITING.equals(message.getMessageType())) {
            closeConnection();   //the client is done.
            return true;
        }

        return false;
    }

    /**
     * This is called when the client exits. This does not mean it succeeded. This is probably the only way you'll get ALL of the client's output as it continues to output things like error
     * messages after it sends us an executionFinished message.
     *
     * @param returnCode the return code of the application
     * @param output its total output
     */
    protected void reportClientExit(boolean wasPremature, int returnCode, String output) {
        //Note: we're relying on clientExited to be called to mark a task as complete.
        //This is because even though gradle sends us a message that it has completed
        //the build, it hasn't yet output all of its information which is very useful
        //for debugging.
        executionInteraction.reportExecutionFinished(returnCode == 0, output, null);
    }

    /**
     * Notification of any status that might be helpful to the user.
     *
     * @param status a status message
     */
    protected void addStatus(String status) {
        executionInteraction.reportLiveOutput(status);
    }

    /**
     * This is called before we execute a command. Here, return an init script for this protocol. An init script is a gradle script that gets run before the other scripts are processed. This is useful
     * here for initiating the gradle client that talks to the server.
     *
     * @return The path to an init script. Null if you have no init script.
     */
    public File getInitScriptFile() {
        return extractInitScriptFile(ExecuteGradleCommandServerProtocol.class, INIT_SCRIPT_NAME);
    }
}
