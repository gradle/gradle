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

import org.gradle.api.logging.LogLevel;
import org.gradle.foundation.ProjectView;
import org.gradle.foundation.ipc.basic.ProcessLauncherServer;
import org.gradle.foundation.ipc.gradle.ExecuteGradleCommandServerProtocol;
import org.gradle.foundation.ipc.gradle.TaskListServerProtocol;
import org.gradle.foundation.queue.ExecutionQueue;
import org.gradle.gradleplugin.foundation.GradlePluginLord;
import org.gradle.logging.ShowStacktrace;

import java.io.File;
import java.util.List;

/**
 * This represents a request to gradle that is executed in a separate process using the ProcessLauncherServer. This is a special request where the results are to build up a project/task tree.
 */
public class RefreshTaskListRequest extends AbstractRequest {

    public static final Type TYPE = new Type() {
    };

    private GradlePluginLord gradlePluginLord;

    public RefreshTaskListRequest(long requestID, String fullCommandLine, ExecutionQueue.RequestCancellation cancellation, GradlePluginLord gradlePluginLord) {
        super(requestID, fullCommandLine, "Refresh", false, cancellation);
        this.gradlePluginLord = gradlePluginLord;
    }

    /**
     * This is called right before this command is executed (because the settings such as log level and stack trace level can be changed between the time someone initiates a command and it executes).
     * The execution takes place in another process so this should create the appropriate Protocol suitable for passing the results of the execution back to us.
     *
     * @param logLevel the user's log level.
     * @param stackTraceLevel the user's stack trace level
     * @param currentDirectory the current working directory of your gradle project
     * @param gradleHomeDirectory the gradle home directory
     * @param customGradleExecutor the path to a custom gradle executable. May be null.
     * @return a protocol that our server will use to communicate with the launched gradle process.
     */
    public ProcessLauncherServer.Protocol createServerProtocol(LogLevel logLevel, ShowStacktrace stackTraceLevel, File currentDirectory, File gradleHomeDirectory,
                                                               File customGradleExecutor) {
        executionInteraction.reportExecutionStarted();  //go ahead and fire off that the execution has started. It has from the user's standpoint.

        ExecutionInteractionWrapper wrapper = new ExecutionInteractionWrapper(executionInteraction);

        return new TaskListServerProtocol(currentDirectory, gradleHomeDirectory, customGradleExecutor, getFullCommandLine(), logLevel, stackTraceLevel, wrapper);
    }

    private class ExecutionInteractionWrapper implements TaskListServerProtocol.ExecutionInteraction {
        private ExecuteGradleCommandServerProtocol.ExecutionInteraction executionInteraction;

        private ExecutionInteractionWrapper(ExecuteGradleCommandServerProtocol.ExecutionInteraction executionInteraction) {
            this.executionInteraction = executionInteraction;
        }

        /**
         * Notification that gradle has started execution. This may not get called if some error occurs that prevents gradle from running.
         */
        public void reportExecutionStarted() {
            executionInteraction.reportExecutionStarted();
        }

        /**
         * Notification that execution has finished. Note: if the client fails to launch at all, this should still be called.
         *
         * @param wasSuccessful true if gradle was successful (returned 0)
         * @param message the output of gradle if it ran. If it didn't, an error message.
         * @param throwable an exception if one occurred
         */
        public void reportExecutionFinished(boolean wasSuccessful, String message, Throwable throwable) {
            executionInteraction.reportExecutionFinished(wasSuccessful, message, throwable);
        }

        public void projectsPopulated(List<ProjectView> projects) {
            gradlePluginLord.setProjects(projects);
        }

        public void reportLiveOutput(String message) {
            executionInteraction.reportLiveOutput(message);
        }
    }

    public void executeAgain(GradlePluginLord gradlePluginLord) {
        gradlePluginLord.addRefreshRequestToQueue();
    }

    public Type getType() {
        return TYPE;
    }
}
