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

import org.gradle.api.AfterEvaluateListener
import org.gradle.api.DependencyManager
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.dependencies.DependencyManagerFactory
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.util.ConfigureUtil
import org.gradle.api.invocation.Build

/**
 * @author Hans Dockter
 */
class DefaultProject extends AbstractProject {
    public DefaultProject() {
        super();
    }

    public DefaultProject(String name, Project parent, File projectDir, String buildFileName, ScriptSource scriptSource,
                          ClassLoader buildScriptClassLoader, ITaskFactory taskFactory,
                          DependencyManagerFactory dependencyManagerFactory, BuildScriptProcessor buildScriptProcessor,
                          PluginRegistry pluginRegistry, IProjectRegistry projectRegistry,
                          IProjectFactory projectFactory, Build build) {
        super(name, parent, projectDir, buildFileName, scriptSource, buildScriptClassLoader, taskFactory, dependencyManagerFactory,
                buildScriptProcessor, pluginRegistry, projectRegistry, projectFactory, build);
    }

    def property(String name) {
        if (this.metaClass.hasProperty(this, name)) {
            return this.metaClass.getProperty(this, name)
        }
        return propertyMissing(name);
    }
    
    def propertyMissing(String name) {
        if (additionalProperties.keySet().contains(name)) {
            return additionalProperties[name]
        }
        if (convention.hasProperty(name)) {
            return convention."$name"
        }
        if (tasks[name]) {
            return tasks[name]
        }
        DefaultProject projectLooper = parent
        while (projectLooper) {
            if (projectLooper.additionalProperties.keySet().contains(name)) {
                return projectLooper."$name"
            } else if (projectLooper.convention.hasProperty(name)) {
                return projectLooper.convention."$name"
            }
            projectLooper = projectLooper.parent
        }
        throw new MissingPropertyException("$name is unknown property!")
    }

    boolean hasProperty(String name) {
        if (this.metaClass.hasProperty(this, name)) {return true}
        if (additionalProperties.keySet().contains(name)) {return true}
        if (convention.hasProperty(name)) {
            return true
        }
        DefaultProject projectLooper = parent
        while (projectLooper) {
            if (projectLooper.additionalProperties.keySet().contains(name)) {
                return true
            } else if (projectLooper.convention.hasProperty(name)) {
                return true
            }
            projectLooper = projectLooper.parent
        }

        tasks[name] ? true : false
    }

    def methodMissing(String name, args) {
        if (buildScript && buildScript.metaClass.respondsTo(buildScript, name, args)) {
            return buildScript.invokeMethod(name, args)
        }
        if (convention && convention.hasMethod(name, args)) {
            return convention.invokeMethod(name, args)
        }
        if (tasks[name] && args.size() == 1 && args[0] instanceof Closure) {
            return task(name, (Closure) args[0])
        }
        if (this.parent) {return this.parent.invokeMethod(name, args)}
        throw new MissingMethodException(name, this.class, args)
    }

    void setProperty(String name, value) {
        if (this.metaClass.hasProperty(this, name)) {
            this.metaClass.setProperty(this, name, value)
            return
        } else if (convention.hasProperty(name)) {
            convention.setProperty(name, value)
            return
        }
        project.additionalProperties[name] = value
    }

    public Task createTask(String name, Closure action) {
        return createTask(new HashMap(), name, action);
    }

    public Task createTask(Map args, String name, Closure action) {
        return createTask(args, name).doFirst(action);
    }

    public AntBuilder ant(Closure configureClosure) {
        return (AntBuilder) ConfigureUtil.configure(configureClosure, getAnt(), Closure.OWNER_FIRST);
    }

    public DependencyManager dependencies(Closure configureClosure) {
        return (DependencyManager) ConfigureUtil.configure(configureClosure, dependencies);
    }

    public void subprojects(Closure configureClosure) {
        configureProjects(getSubprojects(), configureClosure);
    }

    public void allprojects(Closure configureClosure) {
        configureProjects(getAllprojects(), configureClosure);
    }

    public void configureProjects(Iterable<Project> projects, Closure configureClosure) {
        for (Project project : projects) {
            ConfigureUtil.configure(configureClosure, project);
        }
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

    public void addAfterEvaluateListener(Closure afterEvaluateListener) {
        addAfterEvaluateListener(afterEvaluateListener as AfterEvaluateListener)
    }


}
