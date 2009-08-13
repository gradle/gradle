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
import org.gradle.api.internal.project.IProjectRegistry;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.api.invocation.Build;
import org.gradle.execution.TaskExecuter;
import org.gradle.groovy.scripts.ScriptSourceMappingHandler;

public interface BuildInternal extends Build {
    /**
     * {@inheritDoc}
     */
    ProjectInternal getRootProject();

    /**
     * {@inheritDoc}
     */
    TaskExecuter getTaskGraph();

    /**
     * Returns the default project. This is used to resolve relative names and paths provided on the UI.
     */
    ProjectInternal getDefaultProject();

    IProjectRegistry<ProjectInternal> getProjectRegistry();

    PluginRegistry getPluginRegistry();

    /**
     * Returns the handler that maps script classes to their locations (assuming
     * they are FileScriptSources).  Used when compiling the scripts to update
     * the mapping file.
     */
    ScriptSourceMappingHandler getScriptSourceMappingHandler();

    /**
     * Returns the root classloader to use for the build scripts of this build.
     */
    ClassLoader getBuildScriptClassLoader();

    /**
     * Returns the broadcaster for {@link ProjectEvaluationListener} events for this build
     */
    ProjectEvaluationListener getProjectEvaluationBroadcaster();
}
