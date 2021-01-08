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

import org.gradle.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;

public class ProjectProfile extends Operation {
    private HashMap<String, TaskExecution> tasks = new HashMap<String, TaskExecution>();
    private final ContinuousOperation configurationOperation;
    private String projectPath;

    public ProjectProfile(String projectPath) {
        this.projectPath = projectPath;
        this.configurationOperation = new ContinuousOperation(projectPath);
    }

    /**
     * Gets the task profiling container for the specified task.
     */
    public TaskExecution getTaskProfile(String taskPath) {
        TaskExecution result = tasks.get(taskPath);
        if (result == null) {
            result = new TaskExecution(taskPath);
            tasks.put(taskPath, result);
        }
        return result;
    }

    /**
     * Returns the task executions for this project.
     */
    public CompositeOperation<TaskExecution> getTasks() {
        List<TaskExecution> taskExecutions = CollectionUtils.sort(tasks.values(), slowestFirst());
        return new CompositeOperation<TaskExecution>(taskExecutions);
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

    public String toString() {
        return projectPath;
    }

    @Override
    public String getDescription() {
        return projectPath;
    }

    @Override
    long getElapsedTime() {
        return getTasks().getElapsedTime();
    }
}
