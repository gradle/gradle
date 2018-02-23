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

package org.gradle.tooling.internal.gradle;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class DefaultGradleProject<T> implements Serializable, GradleProjectIdentity {
    private DefaultGradleScript buildScript = new DefaultGradleScript();
    private File buildDirectory;
    private File projectDirectory;
    private List<T> tasks = new LinkedList<T>();
    private String name;
    private String description;
    private DefaultProjectIdentifier projectIdentifier;
    private DefaultGradleProject<T> parent;
    private List<? extends DefaultGradleProject<T>> children = new LinkedList<DefaultGradleProject<T>>();

    public String getName() {
        return name;
    }

    public DefaultGradleProject<T> setName(String name) {
        this.name = name;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public DefaultGradleProject<T> setDescription(String description) {
        this.description = description;
        return this;
    }

    public DefaultGradleProject<T> getParent() {
        return parent;
    }

    public DefaultGradleProject<T> setParent(DefaultGradleProject<T> parent) {
        this.parent = parent;
        return this;
    }

    public Collection<? extends DefaultGradleProject<T>> getChildren() {
        return children;
    }

    public DefaultGradleProject<T> setChildren(List<? extends DefaultGradleProject<T>> children) {
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

    public DefaultGradleProject<T> setProjectIdentifier(DefaultProjectIdentifier projectIdentifier) {
        this.projectIdentifier = projectIdentifier;
        return this;
    }

    public DefaultGradleProject<T> findByPath(String path) {
        if (path.equals(this.getPath())) {
            return this;
        }
        for (DefaultGradleProject<T> child : children) {
            DefaultGradleProject<T> found = child.findByPath(path);
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

    public Collection<T> getTasks() {
        return tasks;
    }

    public DefaultGradleProject<T> setTasks(List<T> tasks) {
        this.tasks = tasks;
        return this;
    }

    public File getBuildDirectory() {
        return buildDirectory;
    }

    public DefaultGradleProject<T> setBuildDirectory(File buildDirectory) {
        this.buildDirectory = buildDirectory;
        return this;
    }

    public File getProjectDirectory() {
        return projectDirectory;
    }

    public DefaultGradleProject<T> setProjectDirectory(File projectDirectory) {
        this.projectDirectory = projectDirectory;
        return this;
    }

    public DefaultGradleScript getBuildScript() {
        return buildScript;
    }
}
