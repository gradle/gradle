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
package org.gradle.api.internal;

import org.gradle.BuildListener;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.plugins.PluginAwareInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.api.invocation.Gradle;
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.PublicBuildPath;
import org.gradle.internal.composite.IncludedBuildInternal;
import org.gradle.internal.scan.UsedByScanPlugin;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceRegistryFactory;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * An internal interface for Gradle that exposed objects and concepts that are not intended for public
 * consumption.
 */
@UsedByScanPlugin
@ServiceScope(Scopes.Build.class)
public interface GradleInternal extends Gradle, PluginAwareInternal {
    /**
     * {@inheritDoc}
     */
    @Override
    ProjectInternal getRootProject() throws IllegalStateException;

    @Override
    @Nullable
    GradleInternal getParent();

    GradleInternal getRoot();

    boolean isRootBuild();

    /**
     * Returns the {@link BuildState} that manages the state of this instance.
     */
    BuildState getOwner();

    /**
     * {@inheritDoc}
     */
    @Override
    TaskExecutionGraphInternal getTaskGraph();

    /**
     * Returns the default project. This is used to resolve relative names and paths provided on the UI.
     */
    ProjectInternal getDefaultProject();

    /**
     * Returns the broadcaster for {@link ProjectEvaluationListener} events for this build
     */
    ProjectEvaluationListener getProjectEvaluationBroadcaster();

    /**
     * The settings for this build.
     *
     * @return the settings for this build
     * @throws IllegalStateException when the build is not loaded yet, see {@link #setSettings(SettingsInternal)}
     */
    SettingsInternal getSettings() throws IllegalStateException;

    /**
     * Called by the BuildLoader after the settings are loaded.
     * Until the BuildLoader is executed, {@link #getSettings()} will throw {@link IllegalStateException}.
     *
     * @param settings The settings for this build.
     */
    void setSettings(SettingsInternal settings);

    /**
     * Called by the BuildLoader after the default project is determined.  Until the BuildLoader
     * is executed, {@link #getDefaultProject()} will return null.
     *
     * @param defaultProject The default project for this build.
     */
    void setDefaultProject(ProjectInternal defaultProject);

    /**
     * Called by the BuildLoader after the root project is determined.  Until the BuildLoader
     * is executed, {@link #getRootProject()} will throw {@link IllegalStateException}.
     *
     * @param rootProject The root project for this build.
     */
    void setRootProject(ProjectInternal rootProject);

    /**
     * Returns the broadcaster for {@link BuildListener} events
     */
    BuildListener getBuildListenerBroadcaster();

    @UsedByScanPlugin
    ServiceRegistry getServices();

    ServiceRegistryFactory getServiceRegistryFactory();

    void setClassLoaderScope(Supplier<? extends ClassLoaderScope> classLoaderScope);

    ClassLoaderScope getClassLoaderScope();

    void setIncludedBuilds(Collection<? extends IncludedBuildInternal> includedBuilds);

    /**
     * Returns a unique path for this build within the current Gradle invocation.
     */
    Path getIdentityPath();

    String contextualize(String description);

    PublicBuildPath getPublicBuildPath();

    /**
     * The basis for project build scripts.
     *
     * It is the Gradle runtime + buildSrc's contributions.
     * This is used as the parent scope for the root project's build script, and all script plugins.
     *
     * This is only on this object for convenience due to legacy.
     * Pre Gradle 6, what is now called {@link SettingsInternal#getBaseClassLoaderScope()} was used as the equivalent scope for project scripts.
     * Since Gradle 6, it does not include buildSrc, whereas this scope does.
     *
     * This method is not named as a property getter to avoid getProperties() invoking it.
     *
     * @throws IllegalStateException if called before {@link #setBaseProjectClassLoaderScope(ClassLoaderScope)}
     */
    ClassLoaderScope baseProjectClassLoaderScope();

    /**
     * @throws IllegalStateException if called more than once
     */
    void setBaseProjectClassLoaderScope(ClassLoaderScope classLoaderScope);

    @Override
    StartParameterInternal getStartParameter();

    ProjectRegistry<ProjectInternal> getProjectRegistry();

    // A separate property, as the public getter does not use a wildcard type and cannot be overridden
    List<? extends IncludedBuildInternal> includedBuilds();
}
