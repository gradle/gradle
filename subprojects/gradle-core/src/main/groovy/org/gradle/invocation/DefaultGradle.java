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

package org.gradle.invocation;

import groovy.lang.Closure;
import org.gradle.BuildListener;
import org.gradle.StartParameter;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.internal.GradleDistributionLocator;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.IProjectRegistry;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ServiceRegistryFactory;
import org.gradle.api.invocation.Gradle;
import org.gradle.execution.TaskGraphExecuter;
import org.gradle.listener.ListenerManager;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.GradleVersion;
import org.gradle.util.MultiParentClassLoader;

import java.io.File;

public class DefaultGradle implements GradleInternal {
    private ProjectInternal rootProject;
    private ProjectInternal defaultProject;
    private TaskGraphExecuter taskGraph;
    private final Gradle parent;
    private StartParameter startParameter;
    private MultiParentClassLoader scriptClassLoader;
    private IProjectRegistry<ProjectInternal> projectRegistry;
    private final ListenerManager listenerManager;
    private final ServiceRegistryFactory services;
    private final GradleDistributionLocator distributionLocator;

    public DefaultGradle(Gradle parent, StartParameter startParameter, ServiceRegistryFactory parentRegistry) {
        this.parent = parent;
        this.startParameter = startParameter;
        this.services = parentRegistry.createFor(this);
        this.listenerManager = services.get(ListenerManager.class);
        projectRegistry = services.get(IProjectRegistry.class);
        taskGraph = services.get(TaskGraphExecuter.class);
        scriptClassLoader = services.get(MultiParentClassLoader.class);
        distributionLocator = services.get(GradleDistributionLocator.class);
    }

    @Override
    public String toString() {
        return rootProject == null ? "build" : String.format("build '%s'", rootProject.getName());
    }

    public Gradle getParent() {
        return parent;
    }

    public String getGradleVersion() {
        return new GradleVersion().getVersion();
    }

    public File getGradleHomeDir() {
        DeprecationLogger.nagUser("Gradle.getGradleHomeDir()");
        return distributionLocator.getGradleHome();
    }

    public File getGradleUserHomeDir() {
        return startParameter.getGradleUserHomeDir();
    }

    public StartParameter getStartParameter() {
        return startParameter;
    }

    public ProjectInternal getRootProject() {
        return rootProject;
    }

    public void setRootProject(ProjectInternal rootProject) {
        this.rootProject = rootProject;
    }

    public ProjectInternal getDefaultProject() {
        return defaultProject;
    }

    public void setDefaultProject(ProjectInternal defaultProject) {
        this.defaultProject = defaultProject;
    }

    public TaskGraphExecuter getTaskGraph() {
        return taskGraph;
    }

    public void setTaskGraph(TaskGraphExecuter taskGraph) {
        this.taskGraph = taskGraph;
    }

    public IProjectRegistry<ProjectInternal> getProjectRegistry() {
        return projectRegistry;
    }

    public MultiParentClassLoader getScriptClassLoader() {
        return scriptClassLoader;
    }

    public ProjectEvaluationListener addProjectEvaluationListener(ProjectEvaluationListener listener) {
        addListener(listener);
        return listener;
    }

    public void removeProjectEvaluationListener(ProjectEvaluationListener listener) {
        removeListener(listener);
    }

    public void beforeProject(Closure closure) {
        listenerManager.addListener(ProjectEvaluationListener.class, "beforeEvaluate", closure);
    }

    public void afterProject(Closure closure) {
        listenerManager.addListener(ProjectEvaluationListener.class, "afterEvaluate", closure);
    }

    @Override
    public void buildStarted(Closure closure) {
        listenerManager.addListener(BuildListener.class, "buildStarted", closure);
    }

    @Override
    public void settingsEvaluated(Closure closure) {
        listenerManager.addListener(BuildListener.class, "settingsEvaluated", closure);
    }

    @Override
    public void projectsLoaded(Closure closure) {
        listenerManager.addListener(BuildListener.class, "projectsLoaded", closure);
    }

    @Override
    public void projectsEvaluated(Closure closure) {
        listenerManager.addListener(BuildListener.class, "projectsEvaluated", closure);
    }

    @Override
    public void buildFinished(Closure closure) {
        listenerManager.addListener(BuildListener.class, "buildFinished", closure);
    }

    public void addListener(Object listener) {
        listenerManager.addListener(listener);
    }

    public void removeListener(Object listener) {
        listenerManager.removeListener(listener);
    }

    public void useLogger(Object logger) {
        listenerManager.useLogger(logger);
    }

    public ProjectEvaluationListener getProjectEvaluationBroadcaster() {
        return listenerManager.getBroadcaster(ProjectEvaluationListener.class);
    }

    public void addBuildListener(BuildListener buildListener) {
        addListener(buildListener);
    }

    public BuildListener getBuildListenerBroadcaster() {
        return listenerManager.getBroadcaster(BuildListener.class);
    }

    public Gradle getGradle() {
        return this;
    }

    public ServiceRegistryFactory getServices() {
        return services;
    }
}
