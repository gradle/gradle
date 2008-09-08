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
import org.gradle.api.internal.dependencies.DependencyManagerFactory;
import org.gradle.groovy.scripts.FileScriptSource;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.StringScriptSource;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class ProjectFactory implements IProjectFactory {
    DependencyManagerFactory dependencyManagerFactory;
    BuildScriptProcessor buildScriptProcessor;
    PluginRegistry pluginRegistry;
    String buildFileName;
    IProjectRegistry projectRegistry;
    ScriptSource embeddedScript;
    ITaskFactory taskFactory;

    public ProjectFactory() {
    }

    public ProjectFactory(ITaskFactory taskFactory, DependencyManagerFactory dependencyManagerFactory, BuildScriptProcessor buildScriptProcessor,
                          PluginRegistry pluginRegistry, String buildFileName, IProjectRegistry projectRegistry, ScriptSource embeddedScript) {
        this.taskFactory = taskFactory;
        this.dependencyManagerFactory = dependencyManagerFactory;
        this.buildScriptProcessor = buildScriptProcessor;
        this.pluginRegistry = pluginRegistry;
        this.buildFileName = buildFileName;
        this.projectRegistry = projectRegistry;
        this.embeddedScript = embeddedScript;
    }

    public DefaultProject createProject(String name, Project parent, File rootDir, File projectDir, ClassLoader buildScriptClassLoader) {
        ScriptSource source;
        if (embeddedScript != null) {
            source = embeddedScript;
        } else if (parent == null) {
            source = new FileScriptSource("build file", new File(rootDir, buildFileName));
        } else {
            source = new FileScriptSource("build file", new File(projectDir, buildFileName));
        }
        return new DefaultProject(name, parent, rootDir, projectDir, buildFileName, source, buildScriptClassLoader, taskFactory,
                dependencyManagerFactory, buildScriptProcessor, pluginRegistry, projectRegistry, this);
    }

    public void reset() {
        projectRegistry.reset();
    }
}