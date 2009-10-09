/*
 * Copyright 2007-2008 the original author or authors.
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

import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.internal.project.*;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.api.internal.initialization.ScriptClassLoaderProvider;
import org.gradle.api.invocation.Gradle;
import org.gradle.execution.TaskGraphExecuter;
import org.gradle.BuildListener;

/**
 * An internal interface for Gradle that exposed objects and concepts that are not intended for public
 * consumption.  
 */
public interface GradleInternal extends Gradle {
    /**
     * {@inheritDoc}
     */
    ProjectInternal getRootProject();

    /**
     * {@inheritDoc}
     */
    TaskGraphExecuter getTaskGraph();

    /**
     * Returns the default project. This is used to resolve relative names and paths provided on the UI.
     */
    ProjectInternal getDefaultProject();

    IProjectRegistry<ProjectInternal> getProjectRegistry();

    PluginRegistry getPluginRegistry();

    /**
     * Returns the root classloader to use for the build scripts of this build.
     */
    ClassLoader getBuildScriptClassLoader();

    /**
     * Set once the buildSrc module has been built.  This allows scripts to use
     * classes defined in the buildSrc project.
     * @param buildScriptClassLoader A ClassLoader that can load classes from the
     *                               buildSrc project.
     */
    void setBuildScriptClassLoader(ClassLoader buildScriptClassLoader);

    /**
     * Returns the provider that has been configured by the initscript {} section
     * in the init scripts.
     */
    ScriptClassLoaderProvider getClassLoaderProvider();

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

    StandardOutputRedirector getStandardOutputRedirector();

    IsolatedAntBuilder getIsolatedAntBuilder();

    ServiceRegistryFactory getServiceRegistryFactory();
}
