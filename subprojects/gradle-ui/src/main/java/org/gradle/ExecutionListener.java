/*
 * Copyright 2007 the original author or authors.
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
package org.gradle;

/**
 * This provides runtime information when executing a build. This is a culmination of other gradle listeners to provide
 * common functionality that a tool using Gradle would commonly need access to. In additional to being informated of
 * when tasks are completed, this also provides easy access to gradle's output.
 *
 * @author mhunsicker
 */
public interface ExecutionListener {
    /**
     * Notification that overall execution has been started. This is only called once at the end.
     */
    public void reportExecutionStarted();

    /**
     * Notification that a single task has completed. Note: the task you kicked off probably executes other tasks and
     * this notifies you of those tasks and provides completion progress.
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

    /**
     * Notification that execution of all tasks has completed. This is only called once at the end.
     *
     * @param wasSuccessful whether or not gradle encountered errors.
     * @param buildResult contains more detailed information about the result of a build.
     * @param output the text that gradle produced. May contain error information, but is usually just status.
     */
    public void reportExecutionFinished(boolean wasSuccessful, BuildResult buildResult, String output);
}
