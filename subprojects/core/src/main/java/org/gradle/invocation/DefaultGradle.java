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

import com.google.common.collect.Lists;
import groovy.lang.Closure;
import org.gradle.BuildAdapter;
import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.api.Project;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.plugins.DefaultObjectConfigurationAction;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.api.internal.project.AbstractPluginAware;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.caching.internal.BuildCacheConfiguration;
import org.gradle.caching.internal.BuildCacheConfigurationInternal;
import org.gradle.configuration.ScriptPluginFactory;
import org.gradle.execution.TaskGraphExecuter;
import org.gradle.initialization.ClassLoaderScopeRegistry;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.installation.CurrentGradleInstallation;
import org.gradle.internal.installation.GradleInstallation;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.ServiceRegistryFactory;
import org.gradle.listener.ActionBroadcast;
import org.gradle.listener.ClosureBackedMethodInvocationDispatch;
import org.gradle.util.GradleVersion;
import org.gradle.util.Path;

import javax.inject.Inject;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.NoSuchElementException;

public class DefaultGradle extends AbstractPluginAware implements GradleInternal {
    private ProjectInternal rootProject;
    private ProjectInternal defaultProject;
    private final GradleInternal parent;
    private final StartParameter startParameter;
    private final ServiceRegistry services;
    private final ListenerBroadcast<BuildListener> buildListenerBroadcast;
    private final ListenerBroadcast<ProjectEvaluationListener> projectEvaluationListenerBroadcast;
    private final Collection<IncludedBuild> includedBuilds = Lists.newArrayList();
    private ActionBroadcast<Project> rootProjectActions = new ActionBroadcast<Project>();
    private Path identityPath;
    private final ClassLoaderScope classLoaderScope;

    public DefaultGradle(GradleInternal parent, StartParameter startParameter, ServiceRegistryFactory parentRegistry) {
        this.parent = parent;
        this.startParameter = startParameter;
        this.services = parentRegistry.createFor(this);
        classLoaderScope = services.get(ClassLoaderScopeRegistry.class).getCoreAndPluginsScope();
        buildListenerBroadcast = getListenerManager().createAnonymousBroadcaster(BuildListener.class);
        projectEvaluationListenerBroadcast = getListenerManager().createAnonymousBroadcaster(ProjectEvaluationListener.class);
        buildListenerBroadcast.add(new BuildAdapter() {
            @Override
            public void projectsLoaded(Gradle gradle) {
                rootProjectActions.execute(rootProject);
                rootProjectActions = null;
            }
        });
    }

    @Override
    public String toString() {
        return rootProject == null ? "build" : ("build '" + rootProject.getName() + "'");
    }

    @Override
    public Path getIdentityPath() {
        Path path = findIdentityPath();
        if (path == null) {
            // Not known yet
            throw new IllegalStateException("Root project has not been attached.");
        }
        return path;
    }

    @Nullable
    @Override
    public Path findIdentityPath() {
        if (identityPath == null) {
            if (parent == null) {
                identityPath = Path.ROOT;
            } else {
                if (rootProject == null) {
                    // Not known yet
                    return null;
                }
                identityPath = parent.getIdentityPath().child(rootProject.getName());
            }
        }
        return identityPath;
    }

    @Override
    public void setIdentityPath(Path path) {
        if (identityPath != null && !path.equals(identityPath)) {
            throw new IllegalStateException("Identity path already set");
        }
        identityPath = path;
    }

    @Override
    public GradleInternal getParent() {
        return parent;
    }

    @Override
    public String getGradleVersion() {
        return GradleVersion.current().getVersion();
    }

    @Override
    public File getGradleHomeDir() {
        GradleInstallation gradleInstallation = getCurrentGradleInstallation().getInstallation();
        return gradleInstallation == null ? null : gradleInstallation.getGradleHome();
    }

    @Override
    public File getGradleUserHomeDir() {
        return startParameter.getGradleUserHomeDir();
    }

    @Override
    public StartParameter getStartParameter() {
        return startParameter;
    }

    @Override
    public ProjectInternal getRootProject() {
        if (rootProject == null) {
            throw new IllegalStateException("The root project is not yet available for " + this + ".");
        }
        return rootProject;
    }

    @Override
    public void setRootProject(ProjectInternal rootProject) {
        this.rootProject = rootProject;
    }

    @Override
    public void rootProject(Action<? super Project> action) {
        if (rootProjectActions != null) {
            rootProjectActions.add(action);
        } else {
            assert rootProject != null;
            action.execute(rootProject);
        }
    }

    @Override
    public void allprojects(final Action<? super Project> action) {
        rootProject(new Action<Project>() {
            public void execute(Project project) {
                project.allprojects(action);
            }
        });
    }

    @Override
    public ProjectInternal getDefaultProject() {
        return defaultProject;
    }

    @Override
    public void setDefaultProject(ProjectInternal defaultProject) {
        this.defaultProject = defaultProject;
    }

    @Inject
    @Override
    public TaskGraphExecuter getTaskGraph() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ProjectEvaluationListener addProjectEvaluationListener(ProjectEvaluationListener listener) {
        addListener(listener);
        return listener;
    }

    @Override
    public void removeProjectEvaluationListener(ProjectEvaluationListener listener) {
        removeListener(listener);
    }

    @Override
    public void beforeProject(Closure closure) {
        projectEvaluationListenerBroadcast.add(new ClosureBackedMethodInvocationDispatch("beforeEvaluate", closure));
    }

    @Override
    public void beforeProject(Action<? super Project> action) {
        projectEvaluationListenerBroadcast.add("beforeEvaluate", action);
    }

    @Override
    public void afterProject(Closure closure) {
        projectEvaluationListenerBroadcast.add(new ClosureBackedMethodInvocationDispatch("afterEvaluate", closure));
    }

    @Override
    public void afterProject(Action<? super Project> action) {
        projectEvaluationListenerBroadcast.add("afterEvaluate", action);
    }

    @Override
    public void buildStarted(Closure closure) {
        buildListenerBroadcast.add(new ClosureBackedMethodInvocationDispatch("buildStarted", closure));
    }

    @Override
    public void buildStarted(Action<? super Gradle> action) {
        buildListenerBroadcast.add("buildStarted", action);
    }

    @Override
    public void settingsEvaluated(Closure closure) {
        buildListenerBroadcast.add(new ClosureBackedMethodInvocationDispatch("settingsEvaluated", closure));
    }

    @Override
    public void settingsEvaluated(Action<? super Settings> action) {
        buildListenerBroadcast.add("settingsEvaluated", action);
    }

    @Override
    public void projectsLoaded(Closure closure) {
        buildListenerBroadcast.add(new ClosureBackedMethodInvocationDispatch("projectsLoaded", closure));
    }

    @Override
    public void projectsLoaded(Action<? super Gradle> action) {
        buildListenerBroadcast.add("projectsLoaded", action);
    }

    @Override
    public void projectsEvaluated(Closure closure) {
        buildListenerBroadcast.add(new ClosureBackedMethodInvocationDispatch("projectsEvaluated", closure));
    }

    @Override
    public void projectsEvaluated(Action<? super Gradle> action) {
        buildListenerBroadcast.add("projectsEvaluated", action);
    }

    @Override
    public void buildFinished(Closure closure) {
        buildListenerBroadcast.add(new ClosureBackedMethodInvocationDispatch("buildFinished", closure));
    }

    @Override
    public void buildFinished(Action<? super BuildResult> action) {
        buildListenerBroadcast.add("buildFinished", action);
    }

    @Override
    public void addListener(Object listener) {
        getListenerManager().addListener(listener);
    }

    @Override
    public void removeListener(Object listener) {
        getListenerManager().removeListener(listener);
    }

    @Override
    public void useLogger(Object logger) {
        getListenerManager().useLogger(logger);
    }

    @Override
    public ProjectEvaluationListener getProjectEvaluationBroadcaster() {
        return projectEvaluationListenerBroadcast.getSource();
    }

    @Override
    public void addBuildListener(BuildListener buildListener) {
        addListener(buildListener);
    }

    @Override
    public BuildListener getBuildListenerBroadcaster() {
        return buildListenerBroadcast.getSource();
    }

    @Override
    public void buildCache(Action<? super BuildCacheConfiguration> action) {
        action.execute(getBuildCache());
    }

    @Override
    public Gradle getGradle() {
        return this;
    }

    @Override
    public Collection<IncludedBuild> getIncludedBuilds() {
        return Collections.unmodifiableCollection(includedBuilds);
    }

    @Override
    public void setIncludedBuilds(Collection<IncludedBuild> includedBuilds) {
        this.includedBuilds.addAll(includedBuilds);
    }

    @Override
    public IncludedBuild includedBuild(final String name) {
        for (IncludedBuild includedBuild : includedBuilds) {
            if (includedBuild.getName().equals(name)) {
                return includedBuild;
            }
        }
        throw new NoSuchElementException("Included build '" + name + "' not found.");
    }

    @Override
    public ServiceRegistry getServices() {
        return services;
    }

    @Inject
    public ServiceRegistryFactory getServiceRegistryFactory() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected DefaultObjectConfigurationAction createObjectConfigurationAction() {
        return new DefaultObjectConfigurationAction(getFileResolver(), getScriptPluginFactory(), getScriptHandlerFactory(), getClassLoaderScope(), this);
    }

    @Override
    public ClassLoaderScope getClassLoaderScope() {
        return classLoaderScope;
    }

    @Inject
    public BuildCacheConfigurationInternal getBuildCache() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected ScriptHandlerFactory getScriptHandlerFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected ScriptPluginFactory getScriptPluginFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected FileResolver getFileResolver() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected CurrentGradleInstallation getCurrentGradleInstallation() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected ListenerManager getListenerManager() {
        throw new UnsupportedOperationException();
    }

    @Inject
    public PluginManagerInternal getPluginManager() {
        throw new UnsupportedOperationException();
    }
}
