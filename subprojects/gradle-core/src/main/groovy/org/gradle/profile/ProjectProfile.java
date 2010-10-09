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

import org.gradle.api.Project;
import org.gradle.api.ProjectState;
import org.gradle.api.Task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ProjectProfile {
    private Project project;
    private long beforeEvaluate;
    private long afterEvaluate;
    private ProjectState state;
    private HashMap<Task, TaskProfile> tasks = new HashMap<Task, TaskProfile>();

    public ProjectProfile(Project project) {
        this.project = project;
    }

    /**
     * Gets the task profiling container for the specified task.
     * @param task
     * @return
     */
    public TaskProfile getTaskProfile(Task task) {
        TaskProfile result = tasks.get(task);
        if (result == null) {
            result = new TaskProfile(task);
            tasks.put(task, result);
        }
        return result;
    }

    /**
     * Gets the list of task profiling containers.
     * @return
     */
    public List<TaskProfile> getTaskProfiles() {
        return new ArrayList<TaskProfile>(tasks.values());
    }

    /**
     * Get the String project path.
     * @return
     */
    public String getPath() {
        return project.getPath();
    }

    /**
     * Should be set with a timestamp right before project evaluation begins.
     * @param beforeEvaluate
     */
    public void setBeforeEvaluate(long beforeEvaluate) {
        this.beforeEvaluate = beforeEvaluate;
    }

    /**
     * Should be set with a timestamp right after proejct evaluation finishes.
     * @param afterEvaluate
     */
    public void setAfterEvaluate(long afterEvaluate) {
        this.afterEvaluate = afterEvaluate;
    }

    /**
     * Gets the state of the project after evaluation finishes.
     * @return
     */
    public ProjectState getState() {
        return state;
    }

    public void setState(ProjectState state) {
        this.state = state;
    }

    /**
     * Get the elapsed time (in mSec) for project evaluation (configuration phase).
     * @return
     */
    public long getElapsedEvaluation() {
        return afterEvaluate - beforeEvaluate;
    }

    /**
     * Get the elapsed time (in mSec) for execution of all tasks.
     * @return
     */
    public long getElapsedTaskExecution() {
        long result = 0;
        for (TaskProfile taskProfile : tasks.values()) {
            result += taskProfile.getElapsedExecution();
        }
        return result;
    }
}

