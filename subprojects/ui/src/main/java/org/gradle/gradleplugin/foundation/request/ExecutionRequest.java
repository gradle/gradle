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
import org.gradle.foundation.ipc.basic.ProcessLauncherServer;
import org.gradle.foundation.ipc.gradle.ExecuteGradleCommandServerProtocol;
import org.gradle.foundation.queue.ExecutionQueue;
import org.gradle.gradleplugin.foundation.GradlePluginLord;
import org.gradle.api.logging.configuration.ShowStacktrace;

import java.io.File;

/**
 * This represents a request to gradle that is executed in a separate process using the ProcessLauncherServer. This version is for directly executing commands in gradle (the most common type of
 * request).
 */
public class ExecutionRequest extends AbstractRequest {

    public static final Type TYPE = new Type() {
    };

    public ExecutionRequest(long requestID, String fullCommandLine, String displayName, boolean forceOutputToBeShown, ExecutionQueue.RequestCancellation cancellation) {
        super(requestID, fullCommandLine, displayName, forceOutputToBeShown, cancellation);
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

        return new ExecuteGradleCommandServerProtocol(currentDirectory, gradleHomeDirectory, customGradleExecutor, getFullCommandLine(), logLevel, stackTraceLevel, executionInteraction);
    }

    public void executeAgain(GradlePluginLord gradlePluginLord) {
        gradlePluginLord.addExecutionRequestToQueue(getFullCommandLine(), getDisplayName(), forceOutputToBeShown());
    }

    public Type getType() {
        return TYPE;
    }
}
