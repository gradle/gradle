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
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier;
import org.gradle.tooling.internal.gradle.GradleProjectIdentity;

import java.io.File;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * An implementation for {@link org.gradle.tooling.model.eclipse.EclipseProject}.
 */
public class DefaultEclipseProject implements Serializable, GradleProjectIdentity {
    private final String name;
    private final String path;
    private DefaultEclipseProject parent;
    private List<DefaultEclipseExternalDependency> classpath;
    private final List<DefaultEclipseProject> children;
    private List<DefaultEclipseSourceDirectory> sourceDirectories;
    private List<DefaultEclipseProjectDependency> projectDependencies;
    private final String description;
    private final File projectDirectory;
    private Iterable<? extends DefaultEclipseTask> tasks;
    private Iterable<? extends DefaultEclipseLinkedResource> linkedResources;
    private DefaultGradleProject gradleProject;
    private List<DefaultEclipseProjectNature> projectNatures;
    private List<DefaultEclipseBuildCommand> buildCommands;
    private DefaultEclipseJavaSourceSettings javaSourceSettings;
    private List<DefaultEclipseClasspathContainer> classpathContainers;
    private DefaultEclipseOutputLocation outputLocation;

    public DefaultEclipseProject(String name, String path, String description, File projectDirectory, Iterable<? extends DefaultEclipseProject> children) {
        this.name = name;
        this.path = path;
        this.description = description;
        this.projectDirectory = projectDirectory;
        this.tasks = Collections.emptyList();
        this.children = Lists.newArrayList(children);
        this.classpath = Collections.emptyList();
        this.sourceDirectories = Collections.emptyList();
        this.projectDependencies = Collections.emptyList();
        this.projectNatures = Collections.emptyList();
        this.buildCommands = Collections.emptyList();
        this.classpathContainers = Collections.emptyList();
    }

    @Override
    public String toString() {
        return "project '" + path + "'";
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public String getDescription() {
        return description;
    }

    public DefaultEclipseProject getParent() {
        return parent;
    }

    public File getProjectDirectory() {
        return projectDirectory;
    }

    public void setParent(DefaultEclipseProject parent) {
        this.parent = parent;
    }

    public List<DefaultEclipseProject> getChildren() {
        return children;
    }

    public Iterable<? extends DefaultEclipseSourceDirectory> getSourceDirectories() {
        return sourceDirectories;
    }

    public void setSourceDirectories(List<DefaultEclipseSourceDirectory> sourceDirectories) {
        this.sourceDirectories = sourceDirectories;
    }

    public Iterable<? extends DefaultEclipseProjectDependency> getProjectDependencies() {
        return projectDependencies;
    }

    public void setProjectDependencies(List<DefaultEclipseProjectDependency> projectDependencies) {
        this.projectDependencies = projectDependencies;
    }

    public List<DefaultEclipseExternalDependency> getClasspath() {
        return classpath;
    }

    public void setClasspath(List<DefaultEclipseExternalDependency> classpath) {
        this.classpath = classpath;
    }

    public Iterable<? extends DefaultEclipseTask> getTasks() {
        return tasks;
    }

    public void setTasks(Iterable<? extends DefaultEclipseTask> tasks) {
        this.tasks = tasks;
    }

    public Iterable<? extends DefaultEclipseLinkedResource> getLinkedResources() {
        return linkedResources;
    }

    public void setLinkedResources(Iterable<? extends DefaultEclipseLinkedResource> linkedResources) {
        this.linkedResources = linkedResources;
    }

    public DefaultGradleProject<?> getGradleProject() {
        return gradleProject;
    }

    public DefaultEclipseProject setGradleProject(DefaultGradleProject gradleProject) {
        this.gradleProject = gradleProject;
        return this;
    }

    public List<DefaultEclipseProjectNature> getProjectNatures() {
        return projectNatures;
    }

    public void setProjectNatures(List<DefaultEclipseProjectNature> projectNatures) {
        this.projectNatures = projectNatures;
    }

    public List<DefaultEclipseBuildCommand> getBuildCommands() {
        return buildCommands;
    }

    public void setBuildCommands(List<DefaultEclipseBuildCommand> buildCommands) {
        this.buildCommands = buildCommands;
    }

    public DefaultEclipseJavaSourceSettings getJavaSourceSettings() {
        return javaSourceSettings;
    }

    public void setJavaSourceSettings(DefaultEclipseJavaSourceSettings javaSourceSettings) {
        this.javaSourceSettings = javaSourceSettings;
    }

    public List<DefaultEclipseClasspathContainer> getClasspathContainers() {
        return classpathContainers;
    }

    public void setClasspathContainers(List<DefaultEclipseClasspathContainer> classpathContainers) {
        this.classpathContainers = classpathContainers;
    }

    public DefaultEclipseOutputLocation getOutputLocation() {
        return outputLocation;
    }

    public void setOutputLocation(DefaultEclipseOutputLocation outputLocation) {
        this.outputLocation = outputLocation;
    }

    public DefaultProjectIdentifier getProjectIdentifier() {
        return gradleProject.getProjectIdentifier();
    }

    @Override
    public String getProjectPath() {
        return getProjectIdentifier().getProjectPath();
    }

    @Override
    public File getRootDir() {
        return getProjectIdentifier().getBuildIdentifier().getRootDir();
    }
}
