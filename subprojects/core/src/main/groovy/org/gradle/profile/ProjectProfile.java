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

import java.util.HashMap;

public class ProjectProfile {
    private final Project project;
    private ProjectState state;
    private HashMap<Task, TaskExecution> tasks = new HashMap<Task, TaskExecution>();
    private final ContinuousOperation evaluation;

    public ProjectProfile(Project project) {
        this.project = project;
        this.evaluation = new EvalutationOperation(project);
    }

    /**
     * Gets the task profiling container for the specified task.
     */
    public TaskExecution getTaskProfile(Task task) {
        TaskExecution result = tasks.get(task);
        if (result == null) {
            result = new TaskExecution(task);
            tasks.put(task, result);
        }
        return result;
    }

    /**
     * Returns the task executions for this project.
     */
    public CompositeOperation<TaskExecution> getTasks() {
        return new CompositeOperation<TaskExecution>(tasks.values());
    }

    /**
     * Get the String project path.
     */
    public String getPath() {
        return project.getPath();
    }

    /**
     * Returns the evaluation time of this project.
     */
    public ContinuousOperation getEvaluation() {
        return evaluation;
    }

    /**
     * Gets the state of the project after evaluation finishes.
     */
    public ProjectState getState() {
        return state;
    }

    public void setState(ProjectState state) {
        this.state = state;
    }
}
