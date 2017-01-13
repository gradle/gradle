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
import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.caching.internal.BuildCacheConfiguration;
import org.gradle.caching.internal.BuildCacheConfigurationInternal;
import org.gradle.execution.TaskGraphExecuter;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.ServiceRegistryFactory;
import org.gradle.util.Path;

import java.util.Collection;

/**
 * An internal interface for Gradle that exposed objects and concepts that are not intended for public
 * consumption.
 */
public interface GradleInternal extends Gradle {
    /**
     * {@inheritDoc}
     */
    ProjectInternal getRootProject();

    GradleInternal getParent();

    /**
     * {@inheritDoc}
     */
    TaskGraphExecuter getTaskGraph();

    /**
     * Returns the default project. This is used to resolve relative names and paths provided on the UI.
     */
    ProjectInternal getDefaultProject();

    /**
     * Returns the broadcaster for {@link ProjectEvaluationListener} events for this build
     */
    ProjectEvaluationListener getProjectEvaluationBroadcaster();

    /**
     * Called by the BuildLoader after the default project is determined.  Until the BuildLoader
     * is executed, {@link #getDefaultProject()} will return null.
     * @param defaultProject The default project for this build.
     */
    void setDefaultProject(ProjectInternal defaultProject);

    /**
     * Called by the BuildLoader after the root project is determined.  Until the BuildLoader
     * is executed, {@link #getRootProject()} will return null.
     @param rootProject The root project for this build.
     */
    void setRootProject(ProjectInternal rootProject);

    /**
     * Returns the broadcaster for {@link BuildListener} events
     */
    BuildListener getBuildListenerBroadcaster();

    ServiceRegistry getServices();

    ServiceRegistryFactory getServiceRegistryFactory();

    ClassLoaderScope getClassLoaderScope();

    /**
     * Configures build cache.
     */
    void buildCache(Action<? super BuildCacheConfiguration> action);

    /**
     * Returns the build cache configuration.
     */
    BuildCacheConfigurationInternal getBuildCache();

    void setIncludedBuilds(Collection<IncludedBuild> includedBuilds);

    /**
     * Returns a unique path for this build within the current Gradle invocation.
     *
     * @throws IllegalStateException When the path is not yet known. The path is often a function of the name of the root project, which is not known when this `Gradle` instance is created.
     */
    Path getIdentityPath() throws IllegalStateException;

    /**
     * Returns a unique path for this build within the current Gradle invocation, or null when not yet known
     */
    @Nullable
    Path findIdentityPath();

    void setIdentityPath(Path path);
}
