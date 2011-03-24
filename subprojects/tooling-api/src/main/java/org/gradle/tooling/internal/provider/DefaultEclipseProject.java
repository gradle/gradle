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
package org.gradle.tooling.internal.provider;

import org.gradle.tooling.internal.protocol.ExternalDependencyVersion1;
import org.gradle.tooling.internal.protocol.TaskVersion1;
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectDependencyVersion2;
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectVersion3;
import org.gradle.tooling.internal.protocol.eclipse.EclipseSourceDirectoryVersion1;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.List;

class DefaultEclipseProject implements EclipseProjectVersion3 {
    private final String name;
    private final String path;
    private EclipseProjectVersion3 parent;
    private final List<ExternalDependencyVersion1> classpath;
    private final List<EclipseProjectVersion3> children;
    private final List<EclipseSourceDirectoryVersion1> sourceDirectories;
    private final List<EclipseProjectDependencyVersion2> projectDependencies;
    private final String description;
    private final File projectDirectory;
    private final Iterable<? extends TaskVersion1> tasks;

    public DefaultEclipseProject(String name, String path, String description, File projectDirectory, Iterable<? extends EclipseProjectVersion3> children,
                                 Iterable<? extends TaskVersion1> tasks, Iterable<? extends EclipseSourceDirectoryVersion1> sourceDirectories,
                                 Iterable<? extends ExternalDependencyVersion1> classpath, Iterable<? extends EclipseProjectDependencyVersion2> projectDependencies) {
        this.name = name;
        this.path = path;
        this.description = description;
        this.projectDirectory = projectDirectory;
        this.tasks = tasks;
        this.children = GUtil.addLists(children);
        this.classpath = GUtil.addLists(classpath);
        this.sourceDirectories = GUtil.addLists(sourceDirectories);
        this.projectDependencies = GUtil.addLists(projectDependencies);
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

    public Iterable<? extends EclipseProjectDependencyVersion2> getProjectDependencies() {
        return projectDependencies;
    }

    public List<ExternalDependencyVersion1> getClasspath() {
        return classpath;
    }

    public Iterable<? extends TaskVersion1> getTasks() {
        return tasks;
    }
}
