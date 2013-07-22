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
package org.gradle.openapi.external.runner;

import java.io.File;

/**
 * .
 * @deprecated Use the tooling API instead.
 */
@Deprecated
public interface GradleRunnerInteractionVersion1 {
    /**
     * @return The root directory of your gradle project. The same directory Where you would run gradle from the command.
     */
    public File getWorkingDirectory();

    @Deprecated
    public enum LogLevel {Quiet, Lifecycle, Debug}

    @Deprecated
    public enum StackTraceLevel {InternalExceptions, Always, AlwaysFull}

    /**
     * @return the log level. This determines the detail level of information reported via reportLiveOutput and reportExecutionFinished.
     */
    public LogLevel getLogLevel();

    /**
     * @return the stack trace level. This determines the detail level of any stack traces should an exception occur.
     */
    public StackTraceLevel getStackTraceLevel();

    /**
     * Notification that overall execution has been started. This is only called once at the end.
     */
    public void reportExecutionStarted();

    /**
     * Notification of the total number of tasks that will be executed. This is called after reportExecutionStarted and before any tasks are executed.
     *
     * @param size the total number of tasks.
     */
    public void reportNumberOfTasksToExecute(int size);

    /**
     * Notification that a single task has completed. Note: the task you kicked off probably executes other tasks and this notifies you of those tasks and provides completion progress.
     *
     * @param currentTaskName the task being executed
     * @param percentComplete the percent complete of all the tasks that make up the task you requested.
     */
    public void reportTaskStarted(String currentTaskName, float percentComplete);

    public void reportTaskComplete(String currentTaskName, float percentComplete);

    /**
     * Report real-time output from gradle and its subsystems (such as ant).
     *
     * @param output a single line of text to show.
     */
    public void reportLiveOutput(String output);

    public void reportExecutionFinished(boolean wasSuccessful, String message, Throwable throwable);

    /**
     * This is called to get a custom gradle executable file. If you don't run gradle.bat or gradle shell script to run gradle, use this to specify what you do run. Note: we're going to pass it the
     * arguments that we would pass to gradle so if you don't like that, see alterCommandLineArguments. Normaly, this should return null.
     *
     * @return the Executable to run gradle command or null to use the default
     */
    public File getCustomGradleExecutable();
}
