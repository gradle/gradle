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
package org.gradle.profile;

import org.gradle.api.tasks.TaskState;

/**
 * Container for task profiling information.
 * This includes timestamps around task execution and the resulting task status.
 */
public class TaskExecution extends ContinuousOperation {

    final static String NO_WORK_MESSAGE = "Did No Work";

    private final String path;
    private TaskState state;

    public TaskExecution(String taskPath) {
        super(taskPath);
        this.path = taskPath;
    }

    /**
     * Gets the string task path.
     */
    public String getPath() {
        return path;
    }

    public String getStatus() {
        return state.getSkipped() ? state.getSkipMessage() : (state.getDidWork()) ? "" : NO_WORK_MESSAGE;
    }

    public TaskState getState() {
        return state;
    }

    public TaskExecution completed(TaskState state) {
        this.state = state;
        return this;
    }
}
