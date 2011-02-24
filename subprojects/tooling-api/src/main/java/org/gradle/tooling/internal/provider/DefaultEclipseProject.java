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
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectDependencyVersion1;
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectVersion2;
import org.gradle.tooling.internal.protocol.eclipse.EclipseSourceDirectoryVersion1;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.List;

class DefaultEclipseProject implements EclipseProjectVersion2 {
    private final String name;
    private final String path;
    private EclipseProjectVersion2 parent;
    private final List<ExternalDependencyVersion1> classpath;
    private final List<EclipseProjectVersion2> children;
    private final List<EclipseSourceDirectoryVersion1> sourceDirectories;
    private final List<EclipseProjectDependencyVersion1> projectDependencies;
    private final File projectDirectory;

    public DefaultEclipseProject(String name, String path, File projectDirectory, Iterable<? extends EclipseProjectVersion2> children, Iterable<? extends EclipseSourceDirectoryVersion1> sourceDirectories, Iterable<? extends ExternalDependencyVersion1> classpath, Iterable<? extends EclipseProjectDependencyVersion1> projectDependencies) {
        this.name = name;
        this.path = path;
        this.projectDirectory = projectDirectory;
        this.children = GUtil.addLists(children);
        this.classpath = GUtil.addLists(classpath);
        this.sourceDirectories = GUtil.addLists(sourceDirectories);
        this.projectDependencies = GUtil.addLists(projectDependencies);
    }

    @Override
    public String toString() {
        return String.format("project '%s'", path);
    }

    public String getName() {
        return name;
    }

    public EclipseProjectVersion2 getParent() {
        return parent;
    }

    public File getProjectDirectory() {
        return projectDirectory;
    }

    public void setParent(EclipseProjectVersion2 parent) {
        this.parent = parent;
    }

    public List<EclipseProjectVersion2> getChildren() {
        return children;
    }

    public Iterable<? extends EclipseSourceDirectoryVersion1> getSourceDirectories() {
        return sourceDirectories;
    }

    public Iterable<? extends EclipseProjectDependencyVersion1> getProjectDependencies() {
        return projectDependencies;
    }

    public List<ExternalDependencyVersion1> getClasspath() {
        return classpath;
    }
}
