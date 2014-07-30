/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.tooling.internal.impl;

import org.gradle.TaskExecutionRequest;
import org.gradle.api.Nullable;
import org.gradle.tooling.internal.protocol.InternalLaunchable;
import org.gradle.tooling.model.TaskSelector;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Data used for {@link org.gradle.tooling.model.TaskSelector}.
 */
public class LaunchableGradleTaskSelector implements TaskSelector, InternalLaunchable, TaskExecutionRequest, Serializable {
    private String name;
    private String displayName;
    private String description;
    private String taskName;
    private String projectPath;
    private boolean isPublic;

    public String getName() {
        return name;
    }

    public LaunchableGradleTaskSelector setName(String name) {
        this.name = name;
        return this;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public LaunchableGradleTaskSelector setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getDisplayName() {
        return displayName;
    }

    public LaunchableGradleTaskSelector setDisplayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    public List<String> getArgs() {
        return Collections.singletonList(taskName);
    }

    public LaunchableGradleTaskSelector setTaskName(String taskName) {
        this.taskName = taskName;
        return this;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public LaunchableGradleTaskSelector setProjectPath(String projectPath) {
        this.projectPath = projectPath;
        return this;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public LaunchableGradleTaskSelector setPublic(boolean isPublic) {
        this.isPublic = isPublic;
        return this;
    }

    @Override
    public String toString() {
        return "LaunchableGradleTaskSelector{"
                + "name='" + name + "' "
                + "description='" + description + "'}";
    }
}
