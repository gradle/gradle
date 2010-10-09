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

import org.gradle.api.Task;
import org.gradle.api.tasks.TaskState;

/**
 * Container for task profiling information.
 * This includes timestamps around task execution and the resulting TaskState.
 */
public class TaskProfile {
    private Task task;
    private long start;
    private long finish;
    private TaskState state;

    public TaskProfile(Task task) {
        this.task = task;
    }

    /**
     * Gets the string task path.
     * @return
     */
    public String getPath() {
        return task.getPath();
    }

    /**
     * Should be called with a time right before task execution begins.
     * @param start
     */
    public void setStart(long start) {
        this.start = start;
    }

    /**
     * Should be called with a time right after task execution finishes.
     * @param finish
     */
    public void setFinish(long finish) {
        this.finish = finish;
    }

    /**
     * Gets the elapsed task execution time (in mSec).
     * @return
     */
    public long getElapsedExecution() {
        return finish - start;
    }

    public TaskState getState() {
        return state;
    }

    public void setState(TaskState state) {
        this.state = state;
    }
}
