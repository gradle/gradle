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

import java.util.HashMap;

public class ProjectProfile {
    private HashMap<Task, TaskExecution> tasks = new HashMap<Task, TaskExecution>();
    private final ContinuousOperation configurationOperation;
    private String projectPath;

    public ProjectProfile(String projectPath) {
        this.projectPath = projectPath;
        this.configurationOperation = new ConfigurationOperation(projectPath);
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
        return projectPath;
    }

    /**
     * Returns the configuration time of this project.
     */
    public ContinuousOperation getConfigurationOperation() {
        return configurationOperation;
    }
}
