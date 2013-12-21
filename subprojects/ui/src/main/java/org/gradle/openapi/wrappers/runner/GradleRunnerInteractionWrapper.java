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
package org.gradle.openapi.wrappers.runner;

import org.gradle.api.logging.LogLevel;
import org.gradle.foundation.ipc.gradle.ExecuteGradleCommandServerProtocol;
import org.gradle.logging.ShowStacktrace;
import org.gradle.openapi.external.runner.GradleRunnerInteractionVersion1;

import java.io.File;

/**
 * Wrapper to shield version changes in GradleRunnerInteractionVersion1 from an external user of gradle open API.
 */
public class GradleRunnerInteractionWrapper implements ExecuteGradleCommandServerProtocol.ExecutionInteraction {
    private GradleRunnerInteractionVersion1 interactionVersion1;

    public GradleRunnerInteractionWrapper(GradleRunnerInteractionVersion1 interactionVersion1) {
        this.interactionVersion1 = interactionVersion1;
    }

    /**
     * @return the log level. This determines the detail level of information reported via reportLiveOutput and reportExecutionFinished.
     */
    public LogLevel getLogLevel() {
        GradleRunnerInteractionVersion1.LogLevel logLevel = interactionVersion1.getLogLevel();
        switch (logLevel) {
            case Quiet:
                return LogLevel.QUIET;
            case Lifecycle:
                return LogLevel.LIFECYCLE;
            case Debug:
                return LogLevel.DEBUG;
        }

        return LogLevel.LIFECYCLE;
    }

    /**
     * @return the stack trace level. This determines the detail level of any stack traces should an exception occur.
     */
    public ShowStacktrace getStackTraceLevel() {
        GradleRunnerInteractionVersion1.StackTraceLevel stackTraceLevel = interactionVersion1.getStackTraceLevel();
        switch (stackTraceLevel) {
            case InternalExceptions:
                return ShowStacktrace.INTERNAL_EXCEPTIONS;
            case Always:
                return ShowStacktrace.ALWAYS;
            case AlwaysFull:
                return ShowStacktrace.ALWAYS_FULL;
        }

        return ShowStacktrace.INTERNAL_EXCEPTIONS;
    }

    /**
     * Notification that overall execution has been started. This is only called once at the end.
     */
    public void reportExecutionStarted() {
        this.interactionVersion1.reportExecutionStarted();
    }

    /**
     * Notification of the total number of tasks that will be executed. This is called after reportExecutionStarted and before any tasks are executed.
     *
     * @param size the total number of tasks.
     */
    public void reportNumberOfTasksToExecute(int size) {
        this.interactionVersion1.reportNumberOfTasksToExecute(size);
    }

    /**
     * Notification that a single task has completed. Note: the task you kicked off probably executes other tasks and this notifies you of those tasks and provides completion progress.
     *
     * @param currentTaskName the task being executed
     * @param percentComplete the percent complete of all the tasks that make up the task you requested.
     */
    public void reportTaskStarted(String currentTaskName, float percentComplete) {
        this.interactionVersion1.reportTaskStarted(currentTaskName, percentComplete);
    }

    public void reportTaskComplete(String currentTaskName, float percentComplete) {
        this.interactionVersion1.reportTaskComplete(currentTaskName, percentComplete);
    }

    /**
     * Report real-time output from gradle and its subsystems (such as ant).
     *
     * @param output a single line of text to show.
     */
    public void reportLiveOutput(String output) {
        this.interactionVersion1.reportLiveOutput(output);
    }

    public void reportExecutionFinished(boolean wasSuccessful, String message, Throwable throwable) {
        this.interactionVersion1.reportExecutionFinished(wasSuccessful, message, throwable);
    }

    /*
    * This is called to get a custom gradle executable file. If you don't run
    * gradle.bat or gradle shell script to run gradle, use this to specify
    * what you do run. Note: we're going to pass it the arguments that we would
    * pass to gradle so if you don't like that, see alterCommandLineArguments.
    * Normaly, this should return null.
    * @return the Executable to run gradle command or null to use the default
    */
    public File getCustomGradleExecutable() {
        return interactionVersion1.getCustomGradleExecutable();
    }
}
