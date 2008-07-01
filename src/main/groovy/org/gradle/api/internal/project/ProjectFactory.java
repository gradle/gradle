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

import org.gradle.api.DependencyManagerFactory;
import org.gradle.api.internal.project.BuildScriptProcessor;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.api.internal.project.PluginRegistry;
import org.gradle.api.Project;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class ProjectFactory {
    DependencyManagerFactory dependencyManagerFactory;
    BuildScriptProcessor buildScriptProcessor;
    PluginRegistry pluginRegistry;
    String buildFileName;
    ProjectRegistry projectRegistry;

    public ProjectFactory() {}

    public ProjectFactory(DependencyManagerFactory dependencyManagerFactory, BuildScriptProcessor buildScriptProcessor,
                   PluginRegistry pluginRegistry, String buildFileName, ProjectRegistry projectRegistry) {
        this.dependencyManagerFactory = dependencyManagerFactory;
        this.buildScriptProcessor = buildScriptProcessor;
        this.pluginRegistry = pluginRegistry;
        this.buildFileName = buildFileName;
        this.projectRegistry = projectRegistry;
    }

    DefaultProject createProject(String name, Project parent, File rootDir, Project rootProject, ClassLoader buildScriptClassLoader) {
        return new DefaultProject(name, parent, rootDir, rootProject, buildFileName, buildScriptClassLoader, this,
                dependencyManagerFactory.createDependencyManager(), buildScriptProcessor, pluginRegistry, projectRegistry);
    }

}