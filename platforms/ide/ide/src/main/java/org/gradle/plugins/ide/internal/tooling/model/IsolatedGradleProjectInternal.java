/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling.model;

import org.gradle.api.NonNullApi;
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier;
import org.gradle.tooling.internal.gradle.GradleProjectIdentity;

import javax.annotation.Nullable;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * Represents a Gradle project, isolated from the project hierarchy.
 * <p>
 * <b>This model is internal, and is NOT part of the public Tooling API.</b>
 */
@NonNullApi
public class IsolatedGradleProjectInternal implements Serializable, GradleProjectIdentity {

    private final DefaultGradleScript buildScript = new DefaultGradleScript();
    private File buildDirectory;
    private File projectDirectory;
    private List<LaunchableGradleTask> tasks = new LinkedList<>();
    private String name;
    private String description;
    private DefaultProjectIdentifier projectIdentifier;

    public String getName() {
        return name;
    }

    public IsolatedGradleProjectInternal setName(String name) {
        this.name = name;
        return this;
    }

    public @Nullable String getDescription() {
        return description;
    }

    public IsolatedGradleProjectInternal setDescription(@Nullable String description) {
        this.description = description;
        return this;
    }

    public String getPath() {
        return projectIdentifier.getProjectPath();
    }

    public DefaultProjectIdentifier getProjectIdentifier() {
        return projectIdentifier;
    }

    @Override
    public String getProjectPath() {
        return projectIdentifier.getProjectPath();
    }

    @Override
    public File getRootDir() {
        return projectIdentifier.getBuildIdentifier().getRootDir();
    }

    public IsolatedGradleProjectInternal setProjectIdentifier(DefaultProjectIdentifier projectIdentifier) {
        this.projectIdentifier = projectIdentifier;
        return this;
    }

    public String toString() {
        return "IsolatedGradleProject{"
            + "path='" + getPath() + '\''
            + '}';
    }

    public Collection<LaunchableGradleTask> getTasks() {
        return tasks;
    }

    public IsolatedGradleProjectInternal setTasks(List<LaunchableGradleTask> tasks) {
        this.tasks = tasks;
        return this;
    }

    public File getBuildDirectory() {
        return buildDirectory;
    }

    public IsolatedGradleProjectInternal setBuildDirectory(File buildDirectory) {
        this.buildDirectory = buildDirectory;
        return this;
    }

    public File getProjectDirectory() {
        return projectDirectory;
    }

    public IsolatedGradleProjectInternal setProjectDirectory(File projectDirectory) {
        this.projectDirectory = projectDirectory;
        return this;
    }

    public DefaultGradleScript getBuildScript() {
        return buildScript;
    }

}
