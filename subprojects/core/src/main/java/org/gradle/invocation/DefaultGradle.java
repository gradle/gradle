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
import org.gradle.api.internal.GradleLifecycleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.plugins.DefaultObjectConfigurationAction;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.api.internal.project.AbstractPluginAware;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.invocation.GradleLifecycle;
import org.gradle.api.services.BuildServiceRegistry;
import org.gradle.configuration.ScriptPluginFactory;
import org.gradle.configuration.internal.ListenerBuildOperationDecorator;
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal;
import org.gradle.initialization.ClassLoaderScopeRegistry;
import org.gradle.initialization.SettingsState;
import org.gradle.internal.Cast;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.PublicBuildPath;
import org.gradle.internal.composite.IncludedBuildInternal;
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.installation.CurrentGradleInstallation;
import org.gradle.internal.installation.GradleInstallation;
import org.gradle.internal.resource.TextUriResourceLoader;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.ServiceRegistryFactory;
import org.gradle.util.GradleVersion;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.Closeable;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public abstract class DefaultGradle extends AbstractPluginAware implements GradleInternal, Closeable {

    private SettingsState settings;
    private ProjectInternal defaultProject;
    private final BuildState parent;
    private final StartParameter startParameter;
    private final ServiceRegistry services;
    private List<IncludedBuildInternal> includedBuilds;
    private Path identityPath;
    private Supplier<? extends ClassLoaderScope> classLoaderScope;
    private ClassLoaderScope baseProjectClassLoaderScope;
    private final GradleLifecycleInternal lifecycle;

    public DefaultGradle(@Nullable BuildState parent, StartParameter startParameter, ServiceRegistryFactory parentRegistry) {
        this.parent = parent;
        this.startParameter = startParameter;
        this.services = parentRegistry.createFor(this);
        this.lifecycle = services.get(GradleLifecycleInternal.class);
        if (parent == null) {
            services.get(GradleEnterprisePluginManager.class).registerMissingPluginWarning(this);
        }
    }

    @Override
    public String toString() {
        return lifecycle.hasRootProject() ? ("build '" + getRootProject().getName() + "'") : "build";
    }

    @Override
    public Path getIdentityPath() {
        if (identityPath == null) {
            identityPath = services.get(PublicBuildPath.class).getBuildPath();
        }
        return identityPath;
    }

    @Override
    public String contextualize(String description) {
        if (isRootBuild()) {
            return description;
        } else {
            return description + " (" + getIdentityPath().getPath() + ")";
        }
    }

    @Override
    public GradleInternal getParent() {
        return parent == null ? null : parent.getMutableModel();
    }

    @Override
    public GradleInternal getRoot() {
        GradleInternal parent = getParent();
        if (parent == null) {
            return this;
        } else {
            return parent.getRoot();
        }
    }

    @Override
    public boolean isRootBuild() {
        return parent == null;
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
    public StartParameterInternal getStartParameter() {
        return (StartParameterInternal) startParameter;
    }

    @Override
    public void resetState() {
        classLoaderScope = null;
        baseProjectClassLoaderScope = null;
        defaultProject = null;
        includedBuilds = null;
        lifecycle.resetState();
        getTaskGraph().resetState();
        if (settings != null) {
            settings.close();
            settings = null;
        }
    }

    @Override
    public void close() {
        if (settings != null) {
            settings.close();
            settings = null;
        }
    }

    @Override
    public ClassLoaderScope baseProjectClassLoaderScope() {
        if (baseProjectClassLoaderScope == null) {
            throw new IllegalStateException("baseProjectClassLoaderScope not yet set");
        }
        return baseProjectClassLoaderScope;
    }

    @Override
    public void setBaseProjectClassLoaderScope(ClassLoaderScope classLoaderScope) {
        if (classLoaderScope == null) {
            throw new IllegalArgumentException("classLoaderScope must not be null");
        }
        if (baseProjectClassLoaderScope != null) {
            throw new IllegalStateException("baseProjectClassLoaderScope is already set");
        }

        this.baseProjectClassLoaderScope = classLoaderScope;
    }

    @Override
    public SettingsInternal getSettings() {
        if (settings == null) {
            throw new IllegalStateException("The settings are not yet available for " + this + ".");
        }
        return settings.getSettings();
    }

    @Override
    public void attachSettings(@Nullable SettingsState settings) {
        if (this.settings != null) {
            this.settings.close();
        }
        this.settings = settings;
    }

    @Override
    public ProjectInternal getRootProject() {
        return lifecycle.getRootProject(this);
    }

    @Override
    public void setRootProject(ProjectInternal rootProject) {
        lifecycle.setRootProject(rootProject);
    }

    @Override
    public void rootProject(Action<? super Project> action) {
        lifecycle.rootProject(action);
    }

    @Override
    public void allprojects(final Action<? super Project> action) {
        lifecycle.allprojects(action);
    }

    @Override
    public ProjectInternal getDefaultProject() {
        if (defaultProject == null) {
            throw new IllegalStateException("The default project is not yet available for " + this + ".");
        }
        return defaultProject;
    }

    @Override
    public void setDefaultProject(ProjectInternal defaultProject) {
        this.defaultProject = defaultProject;
    }

    @Inject
    @Override
    public abstract TaskExecutionGraphInternal getTaskGraph();

    @Override
    public ProjectEvaluationListener addProjectEvaluationListener(ProjectEvaluationListener listener) {
        addListener("Gradle.addProjectEvaluationListener", listener);
        return listener;
    }

    @Override
    public void removeProjectEvaluationListener(ProjectEvaluationListener listener) {
        removeListener(listener);
    }

    @Override
    public void beforeProject(Closure closure) {
        lifecycle.beforeProject(closure);
    }

    @Override
    public void beforeProject(Action<? super Project> action) {
        lifecycle.beforeProject(action);
    }

    @Override
    public void afterProject(Closure closure) {
        lifecycle.afterProject(closure);
    }

    @Override
    public void afterProject(Action<? super Project> action) {
        lifecycle.afterProject(action);
    }

    @Override
    public void beforeSettings(Closure<?> closure) {
        lifecycle.beforeSettings(closure);
    }

    @Override
    public void beforeSettings(Action<? super Settings> action) {
        lifecycle.beforeSettings(action);
    }

    @Override
    public void settingsEvaluated(Closure closure) {
        lifecycle.settingsEvaluated(closure);
    }

    @Override
    public void settingsEvaluated(Action<? super Settings> action) {
        lifecycle.settingsEvaluated(action);
    }

    @Override
    public void projectsLoaded(Closure closure) {
        lifecycle.projectsLoaded(closure);
    }

    @Override
    public void projectsLoaded(Action<? super Gradle> action) {
        lifecycle.projectsLoaded(action);
    }

    @Override
    public void projectsEvaluated(Closure closure) {
        lifecycle.projectsEvaluated(closure);
    }

    @Override
    public void projectsEvaluated(Action<? super Gradle> action) {
        lifecycle.projectsEvaluated(action);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void buildFinished(Closure closure) {
        lifecycle.buildFinished(closure);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void buildFinished(Action<? super BuildResult> action) {
        lifecycle.buildFinished(action);
    }

    @Override
    public void addListener(Object listener) {
        addListener("Gradle.addListener", listener);
    }

    @Override
    public void removeListener(Object listener) {
        // do same decoration as in addListener to remove correctly
        getListenerManager().removeListener(decorateUnknownListener(null, listener));
    }

    private void addListener(String registrationPoint, Object listener) {
        lifecycle.notifyListenerRegistration(registrationPoint, listener);
        getListenerManager().addListener(decorateUnknownListener(registrationPoint, listener));
    }

    private Object decorateUnknownListener(String registrationPoint, Object listener) {
        return getListenerBuildOperationDecorator().decorateUnknownListener(registrationPoint, listener);
    }

    @Override
    public void useLogger(Object logger) {
        lifecycle.notifyListenerRegistration("Gradle.useLogger", logger);
        getListenerManager().useLogger(logger);
    }

    @Override
    public ProjectEvaluationListener getProjectEvaluationBroadcaster() {
        return lifecycle.getProjectEvaluationBroadcaster();
    }

    @Override
    public void addBuildListener(BuildListener buildListener) {
        addListener("Gradle.addBuildListener", buildListener);
    }

    @Override
    public BuildListener getBuildListenerBroadcaster() {
        return lifecycle.getBuildListenerBroadcaster();
    }

    @Override
    public Gradle getGradle() {
        return this;
    }

    @Override
    @Inject
    public abstract BuildServiceRegistry getSharedServices();

    @Override
    public Collection<IncludedBuild> getIncludedBuilds() {
        return Cast.uncheckedCast(includedBuilds());
    }

    @Override
    public void setIncludedBuilds(Collection<? extends IncludedBuildInternal> includedBuilds) {
        this.includedBuilds = ImmutableList.copyOf(includedBuilds);
    }

    @Override
    public List<? extends IncludedBuildInternal> includedBuilds() {
        if (includedBuilds == null) {
            throw new IllegalStateException("Included builds are not yet available for this build.");
        }
        return includedBuilds;
    }

    @Override
    public IncludedBuild includedBuild(final String name) {
        for (IncludedBuild includedBuild : includedBuilds()) {
            if (includedBuild.getName().equals(name)) {
                return includedBuild;
            }
        }
        throw new UnknownDomainObjectException("Included build '" + name + "' not found in " + this + ".");
    }

    @Override
    public ServiceRegistry getServices() {
        return services;
    }

    @Override
    protected DefaultObjectConfigurationAction createObjectConfigurationAction() {
        return new DefaultObjectConfigurationAction(
            getFileResolver(),
            getScriptPluginFactory(),
            getScriptHandlerFactory(),
            getClassLoaderScope(),
            getResourceLoaderFactory(),
            this
        );
    }

    @Override
    public void setClassLoaderScope(Supplier<? extends ClassLoaderScope> classLoaderScope) {
        if (this.classLoaderScope != null) {
            throw new IllegalStateException("Class loader scope already used");
        }
        this.classLoaderScope = classLoaderScope;
    }

    @Override
    public ClassLoaderScope getClassLoaderScope() {
        if (classLoaderScope == null) {
            classLoaderScope = () -> getClassLoaderScopeRegistry().getCoreAndPluginsScope();
        }
        return classLoaderScope.get();
    }

    @Override
    public GradleLifecycle getLifecycle() {
        return lifecycle;
    }

    @Inject
    protected abstract ClassLoaderScopeRegistry getClassLoaderScopeRegistry();

    @Override
    @Inject
    public abstract ProjectRegistry<ProjectInternal> getProjectRegistry();

    @Inject
    protected abstract TextUriResourceLoader.Factory getResourceLoaderFactory();

    @Inject
    protected abstract ScriptHandlerFactory getScriptHandlerFactory();

    @Inject
    protected abstract ScriptPluginFactory getScriptPluginFactory();

    @Inject
    protected abstract FileResolver getFileResolver();

    @Inject
    protected abstract CurrentGradleInstallation getCurrentGradleInstallation();

    @Inject
    protected abstract ListenerManager getListenerManager();

    @Inject
    protected abstract ListenerBuildOperationDecorator getListenerBuildOperationDecorator();

    @Override
    @Inject
    public abstract PluginManagerInternal getPluginManager();

    @Override
    @Inject
    public abstract PublicBuildPath getPublicBuildPath();
}
