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

import com.google.common.collect.ImmutableList;
import groovy.lang.Closure;
import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.MutationGuards;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.plugins.DefaultObjectConfigurationAction;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.api.internal.project.AbstractPluginAware;
import org.gradle.api.internal.project.CrossProjectConfigurator;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.configuration.ScriptPluginFactory;
import org.gradle.configuration.internal.ListenerBuildOperationDecorator;
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal;
import org.gradle.initialization.ClassLoaderScopeRegistry;
import org.gradle.internal.InternalBuildAdapter;
import org.gradle.internal.MutableActionSet;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.MutablePublicBuildPath;
import org.gradle.internal.build.PublicBuildPath;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.installation.CurrentGradleInstallation;
import org.gradle.internal.installation.GradleInstallation;
import org.gradle.internal.resource.TextResourceLoader;
import org.gradle.internal.scan.config.BuildScanConfigInit;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.ServiceRegistryFactory;
import org.gradle.listener.ClosureBackedMethodInvocationDispatch;
import org.gradle.util.GradleVersion;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.Collection;

public class DefaultGradle extends AbstractPluginAware implements GradleInternal {
    private SettingsInternal settings;
    private ProjectInternal rootProject;
    private ProjectInternal defaultProject;
    private final GradleInternal parent;
    private final StartParameter startParameter;
    private final ServiceRegistry services;
    private final ListenerBroadcast<BuildListener> buildListenerBroadcast;
    private final ListenerBroadcast<ProjectEvaluationListener> projectEvaluationListenerBroadcast;
    private final CrossProjectConfigurator crossProjectConfigurator;
    private Collection<IncludedBuild> includedBuilds;
    private MutableActionSet<Project> rootProjectActions = new MutableActionSet<Project>();
    private boolean projectsLoaded;
    private Path identityPath;
    private final ClassLoaderScope classLoaderScope;
    private BuildType buildType = BuildType.NONE;

    public DefaultGradle(GradleInternal parent, StartParameter startParameter, ServiceRegistryFactory parentRegistry) {
        this.parent = parent;
        this.startParameter = startParameter;
        this.services = parentRegistry.createFor(this);
        this.crossProjectConfigurator = services.get(CrossProjectConfigurator.class);
        classLoaderScope = services.get(ClassLoaderScopeRegistry.class).getCoreAndPluginsScope();
        buildListenerBroadcast = getListenerManager().createAnonymousBroadcaster(BuildListener.class);
        projectEvaluationListenerBroadcast = getListenerManager().createAnonymousBroadcaster(ProjectEvaluationListener.class);

        buildListenerBroadcast.add(new InternalBuildAdapter() {
            @Override
            public void projectsLoaded(Gradle gradle) {
                if (!rootProjectActions.isEmpty()) {
                    services.get(CrossProjectConfigurator.class).rootProject(rootProject, rootProjectActions);
                }
                projectsLoaded = true;
            }
        });

        services.get(MutablePublicBuildPath.class).set(this);

        if (parent == null) {
            services.get(BuildScanConfigInit.class).init();
        }
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
                if (settings == null) {
                    // Not known yet
                    return null;
                }
                Path parentIdentityPath = parent.findIdentityPath();
                if (parentIdentityPath == null) {
                    // Not known yet
                    return null;
                }
                this.identityPath = parentIdentityPath.child(settings.getRootProject().getName());
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
    public String contextualize(String description) {
        if (getParent() == null) {
            return description;
        } else {
            Path contextPath = findIdentityPath();
            String context = contextPath == null ? getStartParameter().getCurrentDir().getName() : contextPath.getPath();
            return description + " (" + context + ")";
        }
    }

    @Override
    public GradleInternal getParent() {
        return parent;
    }

    @Override
    public GradleInternal getRoot() {
        GradleInternal root = this;
        while (root.getParent() != null) {
            root = root.getParent();
        }
        return root;
    }

    @Override
    public BuildState getOwner() {
        return getServices().get(BuildState.class);
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
    public SettingsInternal getSettings() {
        if (settings == null) {
            throw new IllegalStateException("The settings are not yet available for " + this + ".");
        }
        return settings;
    }

    @Override
    public void setSettings(SettingsInternal settings) {
        this.settings = settings;
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
        rootProject("Gradle.rootProject", action);
    }

    private void rootProject(String registrationPoint, Action<? super Project> action) {
        if (projectsLoaded) {
            assert rootProject != null;
            action.execute(rootProject);
        } else {
            // only need to decorate when this callback is delayed
            rootProjectActions.add(getListenerBuildOperationDecorator().decorate(registrationPoint, action));
        }
    }

    @Override
    public void allprojects(final Action<? super Project> action) {
        rootProject("Gradle.allprojects", new Action<Project>() {
            @Override
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
    public TaskExecutionGraphInternal getTaskGraph() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ProjectEvaluationListener addProjectEvaluationListener(ProjectEvaluationListener listener) {
        addListener("Gradle.addProjectEvaluationListener", listener);
        return listener;
    }

    @Override
    public void removeProjectEvaluationListener(ProjectEvaluationListener listener) {
        removeListener(listener);
    }

    private void assertProjectMutatingMethodAllowed(String methodName) {
        MutationGuards.of(crossProjectConfigurator).assertMutationAllowed(methodName, this, Gradle.class);
    }

    @Override
    public void beforeProject(Closure closure) {
        assertProjectMutatingMethodAllowed("beforeProject(Closure)");
        projectEvaluationListenerBroadcast.add(new ClosureBackedMethodInvocationDispatch("beforeEvaluate", getListenerBuildOperationDecorator().decorate("Gradle.beforeProject", closure)));
    }

    @Override
    public void beforeProject(Action<? super Project> action) {
        assertProjectMutatingMethodAllowed("beforeProject(Action)");
        projectEvaluationListenerBroadcast.add("beforeEvaluate", getListenerBuildOperationDecorator().decorate("Gradle.beforeProject", action));
    }

    @Override
    public void afterProject(Closure closure) {
        assertProjectMutatingMethodAllowed("afterProject(Closure)");
        projectEvaluationListenerBroadcast.add(new ClosureBackedMethodInvocationDispatch("afterEvaluate", getListenerBuildOperationDecorator().decorate("Gradle.afterProject", closure)));
    }

    @Override
    public void afterProject(Action<? super Project> action) {
        assertProjectMutatingMethodAllowed("afterProject(Action)");
        projectEvaluationListenerBroadcast.add("afterEvaluate", getListenerBuildOperationDecorator().decorate("Gradle.afterProject", action));
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
        assertProjectMutatingMethodAllowed("projectsLoaded(Closure)");
        buildListenerBroadcast.add(new ClosureBackedMethodInvocationDispatch("projectsLoaded", getListenerBuildOperationDecorator().decorate("Gradle.projectsLoaded", closure)));
    }

    @Override
    public void projectsLoaded(Action<? super Gradle> action) {
        assertProjectMutatingMethodAllowed("projectsLoaded(Action)");
        buildListenerBroadcast.add("projectsLoaded", getListenerBuildOperationDecorator().decorate("Gradle.projectsLoaded", action));
    }

    @Override
    public void projectsEvaluated(Closure closure) {
        assertProjectMutatingMethodAllowed("projectsEvaluated(Closure)");
        buildListenerBroadcast.add(new ClosureBackedMethodInvocationDispatch("projectsEvaluated", getListenerBuildOperationDecorator().decorate("Gradle.projectsEvaluated", closure)));
    }

    @Override
    public void projectsEvaluated(Action<? super Gradle> action) {
        assertProjectMutatingMethodAllowed("projectsEvaluated(Action)");
        buildListenerBroadcast.add("projectsEvaluated", getListenerBuildOperationDecorator().decorate("Gradle.projectsEvaluated", action));
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
        addListener("Gradle.addListener", listener);
    }

    private void addListener(String registrationPoint, Object listener) {
        getListenerManager().addListener(getListenerBuildOperationDecorator().decorateUnknownListener(registrationPoint, listener));
    }

    @Override
    public void removeListener(Object listener) {
        // do same decoration as in addListener to remove correctly
        getListenerManager().removeListener(getListenerBuildOperationDecorator().decorateUnknownListener(null, listener));
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
        addListener("Gradle.addBuildListener", buildListener);
    }

    @Override
    public BuildListener getBuildListenerBroadcaster() {
        return buildListenerBroadcast.getSource();
    }

    @Override
    public Gradle getGradle() {
        return this;
    }

    @Override
    public Collection<IncludedBuild> getIncludedBuilds() {
        if (includedBuilds == null) {
            throw new IllegalStateException("Included builds are not yet available for this build.");
        }
        return includedBuilds;
    }

    @Override
    public void setIncludedBuilds(Collection<? extends IncludedBuild> includedBuilds) {
        this.includedBuilds = ImmutableList.copyOf(includedBuilds);
    }

    @Override
    public IncludedBuild includedBuild(final String name) {
        for (IncludedBuild includedBuild : getIncludedBuilds()) {
            if (includedBuild.getName().equals(name)) {
                return includedBuild;
            }
        }
        throw new UnknownDomainObjectException("Included build '" + name + "' not found in " + toString() + ".");
    }

    @Override
    public ServiceRegistry getServices() {
        return services;
    }

    @Override
    @Inject
    public ServiceRegistryFactory getServiceRegistryFactory() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected DefaultObjectConfigurationAction createObjectConfigurationAction() {
        return new DefaultObjectConfigurationAction(getFileResolver(), getScriptPluginFactory(), getScriptHandlerFactory(), getClassLoaderScope(), getResourceLoader(), this);
    }

    @Override
    public ClassLoaderScope getClassLoaderScope() {
        return classLoaderScope;
    }

    @Inject
    protected TextResourceLoader getResourceLoader() {
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
    protected ListenerBuildOperationDecorator getListenerBuildOperationDecorator() {
        throw new UnsupportedOperationException();
    }

    @Override
    @Inject
    public PluginManagerInternal getPluginManager() {
        throw new UnsupportedOperationException();
    }

    @Override
    @Inject
    public PublicBuildPath getPublicBuildPath() {
        throw new UnsupportedOperationException();
    }

    @Override
    public BuildType getBuildType() {
        return buildType;
    }

    @Override
    public void setBuildType(BuildType buildType) {
        this.buildType = buildType;
    }
}
