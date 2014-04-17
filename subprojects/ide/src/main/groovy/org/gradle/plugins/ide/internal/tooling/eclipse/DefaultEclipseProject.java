/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.ide.internal.tooling.eclipse;

import com.google.common.collect.Lists;
import org.gradle.tooling.internal.gradle.DefaultGradleProject;
import org.gradle.tooling.internal.gradle.GradleProjectIdentity;
import org.gradle.tooling.internal.protocol.ExternalDependencyVersion1;
import org.gradle.tooling.internal.protocol.eclipse.*;

import java.io.File;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

public class DefaultEclipseProject implements EclipseProjectVersion3, Serializable, GradleProjectIdentity {
    private final String name;
    private final String path;
    private EclipseProjectVersion3 parent;
    private List<ExternalDependencyVersion1> classpath;
    private final List<EclipseProjectVersion3> children;
    private List<EclipseSourceDirectoryVersion1> sourceDirectories;
    private List<EclipseProjectDependencyVersion2> projectDependencies;
    private final String description;
    private final File projectDirectory;
    private Iterable<? extends EclipseTaskVersion1> tasks;
    private Iterable<? extends EclipseLinkedResourceVersion1> linkedResources;
    private DefaultGradleProject gradleProject;

    public DefaultEclipseProject(String name, String path, String description, File projectDirectory, Iterable<? extends EclipseProjectVersion3> children) {
        this.name = name;
        this.path = path;
        this.description = description;
        this.projectDirectory = projectDirectory;
        this.tasks = Collections.emptyList();
        this.children = Lists.newArrayList(children);
        this.classpath = Collections.emptyList();
        this.sourceDirectories = Collections.emptyList();
        this.projectDependencies = Collections.emptyList();
    }

    @Override
    public String toString() {
        return String.format("project '%s'", path);
    }

    public String getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public EclipseProjectVersion3 getParent() {
        return parent;
    }

    public File getProjectDirectory() {
        return projectDirectory;
    }

    public void setParent(EclipseProjectVersion3 parent) {
        this.parent = parent;
    }

    public List<EclipseProjectVersion3> getChildren() {
        return children;
    }

    public Iterable<? extends EclipseSourceDirectoryVersion1> getSourceDirectories() {
        return sourceDirectories;
    }

    public void setSourceDirectories(List<EclipseSourceDirectoryVersion1> sourceDirectories) {
        this.sourceDirectories = sourceDirectories;
    }

    public Iterable<? extends EclipseProjectDependencyVersion2> getProjectDependencies() {
        return projectDependencies;
    }

    public void setProjectDependencies(List<EclipseProjectDependencyVersion2> projectDependencies) {
        this.projectDependencies = projectDependencies;
    }

    public List<ExternalDependencyVersion1> getClasspath() {
        return classpath;
    }
    public void setClasspath(List<ExternalDependencyVersion1> classpath) {
        this.classpath = classpath;
    }

    public Iterable<? extends EclipseTaskVersion1> getTasks() {
        return tasks;
    }

    public void setTasks(Iterable<? extends EclipseTaskVersion1> tasks) {
        this.tasks = tasks;
    }

    public Iterable<? extends EclipseLinkedResourceVersion1> getLinkedResources() {
        return linkedResources;
    }

    public void setLinkedResources(Iterable<? extends EclipseLinkedResourceVersion1> linkedResources) {
        this.linkedResources = linkedResources;
    }

    public DefaultGradleProject<?> getGradleProject() {
        return gradleProject;
    }

    public DefaultEclipseProject setGradleProject(DefaultGradleProject gradleProject) {
        this.gradleProject = gradleProject;
        return this;
    }
}
