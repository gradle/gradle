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

package org.gradle.api.internal.project

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.dsl.RepositoryHandlerFactory
import org.gradle.api.artifacts.repositories.InternalRepository
import org.gradle.api.internal.BuildInternal
import org.gradle.api.internal.artifacts.ConfigurationContainerFactory
import org.gradle.api.internal.artifacts.dsl.PublishArtifactFactory
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory
import org.gradle.api.plugins.Convention
import org.gradle.configuration.ProjectEvaluator
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.util.ConfigureUtil
import org.gradle.api.internal.project.*

/**
 * @author Hans Dockter
 */
class DefaultProject extends AbstractProject {

    public DefaultProject(String name) {
        super(name);
    }

    public DefaultProject(String name, ProjectInternal parent, File projectDir, File buildFile,
                           ScriptSource buildScriptSource, ClassLoader buildScriptClassLoader, ITaskFactory taskFactory,
                           ConfigurationContainerFactory configurationContainerFactory,
                           DependencyFactory dependencyFactory,
                           RepositoryHandlerFactory repositoryHandlerFactory,
                           PublishArtifactFactory publishArtifactFactory,
                           InternalRepository internalRepository,
                           AntBuilderFactory antBuilderFactory,
                           ProjectEvaluator projectEvaluator,
                           PluginRegistry pluginRegistry, IProjectRegistry projectRegistry,
                           BuildInternal build, Convention convention) {
        super(name, parent, projectDir, buildFile, buildScriptSource, buildScriptClassLoader, taskFactory, configurationContainerFactory,
                dependencyFactory, repositoryHandlerFactory, publishArtifactFactory,
                internalRepository, antBuilderFactory, projectEvaluator, pluginRegistry,
                projectRegistry, build, convention);
    }

    def propertyMissing(String name) {
        property(name)
    }

    def methodMissing(String name, args) {
        dynamicObjectHelper.invokeMethod(name, args)
    }

    void setProperty(String name, value) {
        dynamicObjectHelper.setProperty(name, value)
    }

    public AntBuilder ant(Closure configureClosure) {
        return (AntBuilder) ConfigureUtil.configure(configureClosure, getAnt(), Closure.OWNER_FIRST);
    }

    public void subprojects(Closure configureClosure) {
        configure(getSubprojects(), configureClosure);
    }

    public void allprojects(Closure configureClosure) {
        configure(getAllprojects(), configureClosure);
    }

    public Project project(String path, Closure configureClosure) {
        return (Project) ConfigureUtil.configure(configureClosure, project(path));
    }

    public Task task(String path, Closure configureClosure) {
        Task task = task(path);
        if (configureClosure != null) {
            task.configure(configureClosure);
        }
        return task;
    }

    public Object configure(Object object, Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, object)
    }

    public Iterable configure(Iterable objects, Closure configureClosure) {
        objects.each {
            ConfigureUtil.configure(configureClosure, it)
        }
        objects
    }

    public void configurations(Closure configureClosure) {
        getConfigurations().configure(configureClosure)
    }

    public void repositories(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getRepositories())
    }

    public void dependencies(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getDependencies())
    }

    public void artifacts(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getArtifacts())
    }
}
