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
package org.gradle.gradleplugin.foundation.request;

import org.gradle.foundation.ipc.basic.ProcessLauncherServer;
import org.gradle.foundation.ipc.gradle.ExecuteGradleCommandServerProtocol;
import org.gradle.foundation.queue.ExecutionQueue;

/**
 * This represents a basic reques to gradle that is executed in a separate process using the ProcessLauncherServer. This stores the command line to execute and has the ability to cancel itself by
 * either removing it from the queue if it hasn't started yet, or killing the external process.
 */
public abstract class AbstractRequest implements Request {
    private long requestID;
    private String fullCommandLine;
    private String displayName;
    private boolean forceOutputToBeShown;
    private ExecutionQueue.RequestCancellation cancellation;
    private ProcessLauncherServer server;
    protected ExecuteGradleCommandServerProtocol.ExecutionInteraction executionInteraction = new DummyExecutionInteraction();

    public AbstractRequest(long requestID, String fullCommandLine, String displayName, boolean forceOutputToBeShown, ExecutionQueue.RequestCancellation cancellation) {
        this.requestID = requestID;
        this.fullCommandLine = fullCommandLine;
        this.displayName = displayName;
        this.forceOutputToBeShown = forceOutputToBeShown;
        this.cancellation = cancellation;
    }

    public long getRequestID() {
        return requestID;
    }

    public String getFullCommandLine() {
        return fullCommandLine;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean forceOutputToBeShown() {
        return forceOutputToBeShown;
    }

    /**
     * Cancels this request.
     */
    public synchronized boolean cancel() {
        if (this.server != null) {
            server.killProcess();
        }

        cancellation.onCancel(this);
        return true;
    }

    public synchronized void setProcessLauncherServer(ProcessLauncherServer server) {
        this.server = server;
    }

    public void setExecutionInteraction(ExecuteGradleCommandServerProtocol.ExecutionInteraction executionInteraction) {
        this.executionInteraction = executionInteraction;
    }

    /**
     * This is a dummy ExecutionInteraction. It does nothing. It exists because the requests require one, but there's a timing issue about when the Request and ExecutionInteraction are paired.
     * Actually, this mechanism needs to allow for multiple listeners instead of just a single interaction. I was in the middle of refactoring other things and didn't want to get into that, so I'm
     * doing this instead. Its only meant to be temporary, but we'll see.
     */
    public class DummyExecutionInteraction implements ExecuteGradleCommandServerProtocol.ExecutionInteraction {
        /**
         * Notification that gradle has started execution. This may not get called if some error occurs that prevents gradle from running.
         */
        public void reportExecutionStarted() {

        }

        /**
         * Notification of the total number of tasks that will be executed. This is called after reportExecutionStarted and before any tasks are executed.
         *
         * @param size the total number of tasks.
         */
        public void reportNumberOfTasksToExecute(int size) {

        }

        /**
         * Notification that execution has finished. Note: if the client fails to launch at all, this should still be called.
         *
         * @param wasSuccessful true if gradle was successful (returned 0)
         * @param message the output of gradle if it ran. If it didn't, an error message.
         * @param throwable an exception if one occurred
         */
        public void reportExecutionFinished(boolean wasSuccessful, String message, Throwable throwable) {

        }

        public void reportTaskStarted(String message, float percentComplete) {

        }

        public void reportTaskComplete(String message, float percentComplete) {

        }

        public void reportLiveOutput(String message) {

        }
    }
}