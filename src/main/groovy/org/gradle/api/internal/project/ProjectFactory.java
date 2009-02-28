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

import org.gradle.api.internal.BuildInternal;
import org.gradle.api.internal.artifacts.DependencyManagerFactory;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.groovy.scripts.FileScriptSource;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.StringScriptSource;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class ProjectFactory implements IProjectFactory {
    private DependencyManagerFactory dependencyManagerFactory;
    private BuildScriptProcessor buildScriptProcessor;
    private PluginRegistry pluginRegistry;
    private ScriptSource embeddedScript;
    private ITaskFactory taskFactory;
    private AntBuilderFactory antBuilderFactory;

    public ProjectFactory() {
    }

    public ProjectFactory(ITaskFactory taskFactory, DependencyManagerFactory dependencyManagerFactory,
                          BuildScriptProcessor buildScriptProcessor, PluginRegistry pluginRegistry,
                          ScriptSource embeddedScript, AntBuilderFactory antBuilderFactory) {
        this.taskFactory = taskFactory;
        this.dependencyManagerFactory = dependencyManagerFactory;
        this.buildScriptProcessor = buildScriptProcessor;
        this.pluginRegistry = pluginRegistry;
        this.embeddedScript = embeddedScript;
        this.antBuilderFactory = antBuilderFactory;
    }

    public DefaultProject createProject(ProjectDescriptor projectDescriptor, ProjectInternal parent, BuildInternal build) {
        File buildFile = projectDescriptor.getBuildFile();
        ScriptSource source;
        if (embeddedScript != null) {
            source = embeddedScript;
        } else if (!buildFile.exists()) {
            source = new StringScriptSource("empty build file", "");
        } else {
            source = new FileScriptSource("build file", buildFile);
        }

        DefaultProject project = new DefaultProject(projectDescriptor.getName(), parent, projectDescriptor.getProjectDir(),
                projectDescriptor.getBuildFile(), source, build.getBuildScriptClassLoader(), taskFactory,
                dependencyManagerFactory, antBuilderFactory, buildScriptProcessor, pluginRegistry,
                build.getProjectRegistry(), build);
        if (parent != null) {
            parent.addChildProject(project);
        }
        return project;
    }
}