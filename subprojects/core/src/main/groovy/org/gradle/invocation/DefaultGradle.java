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
import org.gradle.BuildAdapter;
import org.gradle.BuildListener;
import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.api.internal.GradleDistributionLocator;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.IProjectRegistry;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ServiceRegistryFactory;
import org.gradle.api.invocation.Gradle;
import org.gradle.execution.TaskGraphExecuter;
import org.gradle.listener.ActionBroadcast;
import org.gradle.listener.ListenerBroadcast;
import org.gradle.listener.ListenerManager;
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
    private final ListenerBroadcast<BuildListener> buildListenerBroadcast;
    private final ListenerBroadcast<ProjectEvaluationListener> projectEvaluationListenerBroadcast;
    private ActionBroadcast<Project> rootProjectActions = new ActionBroadcast<Project>();

    public DefaultGradle(Gradle parent, StartParameter startParameter, ServiceRegistryFactory parentRegistry) {
        this.parent = parent;
        this.startParameter = startParameter;
        this.services = parentRegistry.createFor(this);
        this.listenerManager = services.get(ListenerManager.class);
        projectRegistry = services.get(IProjectRegistry.class);
        taskGraph = services.get(TaskGraphExecuter.class);
        scriptClassLoader = services.get(MultiParentClassLoader.class);
        distributionLocator = services.get(GradleDistributionLocator.class);
        buildListenerBroadcast = listenerManager.createAnonymousBroadcaster(BuildListener.class);
        projectEvaluationListenerBroadcast = listenerManager.createAnonymousBroadcaster(ProjectEvaluationListener.class);
        buildListenerBroadcast.add(new BuildAdapter(){
            @Override
            public void projectsLoaded(Gradle gradle) {
                rootProjectActions.execute(rootProject);
                rootProjectActions = null;
            }
        });
    }

    @Override
    public String toString() {
        return rootProject == null ? "build" : String.format("build '%s'", rootProject.getName());
    }

    public Gradle getParent() {
        return parent;
    }

    public String getGradleVersion() {
        return GradleVersion.current().getVersion();
    }

    public File getGradleHomeDir() {
        return distributionLocator.getGradleHome();
    }

    public File getGradleUserHomeDir() {
        return startParameter.getGradleUserHomeDir();
    }

    public StartParameter getStartParameter() {
        return startParameter;
    }

    public ProjectInternal getRootProject() {
        if (rootProject == null) {
            throw new IllegalStateException("The root project is not yet available for " + this + ".");
        }
        return rootProject;
    }

    public void setRootProject(ProjectInternal rootProject) {
        this.rootProject = rootProject;
    }

    public void rootProject(Action<? super Project> action) {
        if (rootProjectActions != null) {
            rootProjectActions.add(action);
        } else {
            assert rootProject != null;
            action.execute(rootProject);
        }
    }

    public void allprojects(final Action<? super Project> action) {
        rootProject(new Action<Project>() {
            public void execute(Project project) {
                project.allprojects(action);
            }
        });
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
        projectEvaluationListenerBroadcast.add("beforeEvaluate", new ClosureBackedAction<Object>(closure));
    }

    public void afterProject(Closure closure) {
        projectEvaluationListenerBroadcast.add("afterEvaluate", new ClosureBackedAction<Object>(closure));
    }

    public void buildStarted(Closure closure) {
        buildListenerBroadcast.add("buildStarted", new ClosureBackedAction<Object>(closure));
    }

    public void settingsEvaluated(Closure closure) {
        buildListenerBroadcast.add("settingsEvaluated", new ClosureBackedAction<Object>(closure));
    }

    public void projectsLoaded(Closure closure) {
        buildListenerBroadcast.add("projectsLoaded", new ClosureBackedAction<Object>(closure));
    }

    public void projectsEvaluated(Closure closure) {
        buildListenerBroadcast.add("projectsEvaluated", new ClosureBackedAction<Object>(closure));
    }

    public void buildFinished(Closure closure) {
        buildListenerBroadcast.add("buildFinished", new ClosureBackedAction<Object>(closure));
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
        return projectEvaluationListenerBroadcast.getSource();
    }

    public void addBuildListener(BuildListener buildListener) {
        addListener(buildListener);
    }

    public BuildListener getBuildListenerBroadcaster() {
        return buildListenerBroadcast.getSource();
    }

    public Gradle getGradle() {
        return this;
    }

    public ServiceRegistryFactory getServices() {
        return services;
    }
}
