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

import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.artifacts.dsl.RepositoryHandlerFactory;
import org.gradle.api.artifacts.repositories.InternalRepository;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.internal.BuildInternal;
import org.gradle.api.internal.artifacts.ConfigurationContainerFactory;
import org.gradle.api.internal.artifacts.dsl.PublishArtifactFactory;
import org.gradle.api.internal.plugins.DefaultConvention;
import org.gradle.configuration.ProjectEvaluator;
import org.gradle.groovy.scripts.FileScriptSource;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.StringScriptSource;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class ProjectFactory implements IProjectFactory {
    private ConfigurationContainerFactory configurationContainerFactory;
    private DependencyFactory dependencyFactory;
    private ProjectEvaluator projectEvaluator;
    private PluginRegistry pluginRegistry;
    private ScriptSource embeddedScript;
    private ITaskFactory taskFactory;
    private AntBuilderFactory antBuilderFactory;
    private RepositoryHandlerFactory repositoryHandlerFactory;
    private PublishArtifactFactory publishArtifactFactory;
    private InternalRepository internalRepository;

    public ProjectFactory() {
    }

    public ProjectFactory(ITaskFactory taskFactory, ConfigurationContainerFactory configurationContainerFactory,
                          DependencyFactory dependencyFactory,
                          RepositoryHandlerFactory repositoryHandlerFactory,
                          PublishArtifactFactory publishArtifactFactory,
                          InternalRepository internalRepository,
                          ProjectEvaluator projectEvaluator, PluginRegistry pluginRegistry,
                          ScriptSource embeddedScript, AntBuilderFactory antBuilderFactory) {
        this.taskFactory = taskFactory;
        this.configurationContainerFactory = configurationContainerFactory;
        this.dependencyFactory = dependencyFactory;
        this.repositoryHandlerFactory = repositoryHandlerFactory;
        this.publishArtifactFactory = publishArtifactFactory;
        this.internalRepository = internalRepository;
        this.projectEvaluator = projectEvaluator;
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
                configurationContainerFactory,
                dependencyFactory,
                repositoryHandlerFactory,
                publishArtifactFactory,
                internalRepository, antBuilderFactory, projectEvaluator, pluginRegistry,
                build.getProjectRegistry(), build, new DefaultConvention());
        if (parent != null) {
            parent.addChildProject(project);
        }
        return project;
    }
}