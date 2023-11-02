/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier;
import org.gradle.tooling.internal.gradle.GradleProjectIdentity;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class DefaultGradleProject implements Serializable, GradleProjectIdentity {
    private final DefaultGradleScript buildScript = new DefaultGradleScript();
    private File buildDirectory;
    private File projectDirectory;
    private List<LaunchableGradleProjectTask> tasks = new LinkedList<>();
    private String name;
    private String description;
    private DefaultProjectIdentifier projectIdentifier;
    private DefaultGradleProject parent;
    private List<? extends DefaultGradleProject> children = new LinkedList<>();
    private String buildTreePath;

    public String getName() {
        return name;
    }

    public DefaultGradleProject setName(String name) {
        this.name = name;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public DefaultGradleProject setDescription(String description) {
        this.description = description;
        return this;
    }

    public DefaultGradleProject getParent() {
        return parent;
    }

    public DefaultGradleProject setParent(DefaultGradleProject parent) {
        this.parent = parent;
        return this;
    }

    public Collection<? extends DefaultGradleProject> getChildren() {
        return children;
    }

    public DefaultGradleProject setChildren(List<? extends DefaultGradleProject> children) {
        this.children = children;
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

    public DefaultGradleProject setProjectIdentifier(DefaultProjectIdentifier projectIdentifier) {
        this.projectIdentifier = projectIdentifier;
        return this;
    }

    public DefaultGradleProject findByPath(String path) {
        if (path.equals(this.getPath())) {
            return this;
        }
        for (DefaultGradleProject child : children) {
            DefaultGradleProject found = child.findByPath(path);
            if (found != null) {
                return found;
            }
        }

        return null;
    }

    public String toString() {
        return "GradleProject{"
            + "path='" + getPath() + '\''
            + '}';
    }

    public Collection<LaunchableGradleProjectTask> getTasks() {
        return tasks;
    }

    public DefaultGradleProject setTasks(List<LaunchableGradleProjectTask> tasks) {
        this.tasks = tasks;
        return this;
    }

    public File getBuildDirectory() {
        return buildDirectory;
    }

    public DefaultGradleProject setBuildDirectory(File buildDirectory) {
        this.buildDirectory = buildDirectory;
        return this;
    }

    public File getProjectDirectory() {
        return projectDirectory;
    }

    public DefaultGradleProject setProjectDirectory(File projectDirectory) {
        this.projectDirectory = projectDirectory;
        return this;
    }

    public DefaultGradleScript getBuildScript() {
        return buildScript;
    }

    public DefaultGradleProject setBuildTreePath(String buildTreePath) {
        this.buildTreePath = buildTreePath;
        return this;
    }

    public String getBuildTreePath() {
        return buildTreePath;
    }
}
