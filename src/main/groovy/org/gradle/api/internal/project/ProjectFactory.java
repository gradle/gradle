/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.internal.project;

import org.gradle.api.Project;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.internal.dependencies.DependencyManagerFactory;
import org.gradle.api.invocation.Build;
import org.gradle.groovy.scripts.FileScriptSource;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.invocation.DefaultBuild;
import org.gradle.StartParameter;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class ProjectFactory implements IProjectFactory {
    private DependencyManagerFactory dependencyManagerFactory;
    private BuildScriptProcessor buildScriptProcessor;
    private PluginRegistry pluginRegistry;
    private StartParameter startParameter;
    private IProjectRegistry projectRegistry;
    private ScriptSource embeddedScript;
    private ITaskFactory taskFactory;
    private TaskExecutionGraph taskGraph;
    private AntBuilderFactory antBuilderFactory;

    public ProjectFactory() {
    }

    public ProjectFactory(ITaskFactory taskFactory, DependencyManagerFactory dependencyManagerFactory,
                          BuildScriptProcessor buildScriptProcessor, PluginRegistry pluginRegistry,
                          StartParameter startParameter, IProjectRegistry projectRegistry, TaskExecutionGraph taskGraph,
                          ScriptSource embeddedScript, AntBuilderFactory antBuilderFactory) {
        this.taskFactory = taskFactory;
        this.dependencyManagerFactory = dependencyManagerFactory;
        this.buildScriptProcessor = buildScriptProcessor;
        this.pluginRegistry = pluginRegistry;
        this.startParameter = startParameter;
        this.projectRegistry = projectRegistry;
        this.taskGraph = taskGraph;
        this.embeddedScript = embeddedScript;
        this.antBuilderFactory = antBuilderFactory;
    }

    public DefaultProject createProject(String name, Project parent, File projectDir,
                                        ClassLoader buildScriptClassLoader) {
        ScriptSource source;
        if (embeddedScript != null) {
            source = embeddedScript;
        } else {
            source = new FileScriptSource("build file", new File(projectDir, startParameter.getBuildFileName()));
        }
        Build build;
        if (parent == null) {
            build = new DefaultBuild(null, taskGraph, startParameter);
        } else {
            build = parent.getBuild();
        }
        DefaultProject project = new DefaultProject(name, parent, projectDir, startParameter.getBuildFileName(), source,
                buildScriptClassLoader, taskFactory,
                dependencyManagerFactory, antBuilderFactory, buildScriptProcessor, pluginRegistry, projectRegistry, this, build);
        if (parent == null) {
            ((DefaultBuild) build).setRootProject(project.getRootProject());
        }
        return project;
    }

    public void reset() {
        projectRegistry.reset();
    }
}