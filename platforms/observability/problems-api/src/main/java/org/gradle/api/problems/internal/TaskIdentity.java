/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.problems.internal;

public class TaskIdentity {

    private final String taskPath;
    private final String projectPath;
    private final String buildPath;

    /**
     * @param taskPath the path of the task in the build tree
     * @param projectPath the path of the task's project in the build tree
     * @param buildPath the path of the build that owns the project
     */
    public TaskIdentity(String taskPath, String projectPath, String buildPath) {
        this.taskPath = taskPath;
        this.projectPath = projectPath;
        this.buildPath = buildPath;
    }

    public String getTaskPath() {
        return taskPath;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public String getBuildPath() {
        return buildPath;
    }
}
