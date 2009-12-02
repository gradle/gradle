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
import org.gradle.api.internal.GradleInternal
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.util.ConfigureUtil

/**
 * @author Hans Dockter
 */
class DefaultProject extends AbstractProject {


    public DefaultProject(String name) {
        super(name);
    }

    public DefaultProject(String name,
                          ProjectInternal parent,
                          File projectDir,
                          File buildFile,
                          ScriptSource buildScriptSource,
                          GradleInternal gradle,
                          ServiceRegistryFactory serviceRegistryFactory
    ) {
        super(name, parent, projectDir, buildFile, buildScriptSource, gradle, serviceRegistryFactory);
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

    public org.gradle.api.AntBuilder ant(Closure configureClosure) {
        return ConfigureUtil.configure(configureClosure, getAnt());
    }

    public void subprojects(Closure configureClosure) {
        configure(getSubprojects(), configureClosure);
    }

    public void allprojects(Closure configureClosure) {
        configure(getAllprojects(), configureClosure);
    }

    public Project project(String path, Closure configureClosure) {
        return ConfigureUtil.configure(configureClosure, project(path));
    }

    public Object configure(Object object, Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, object)
    }

    public Iterable configure(Iterable objects, Closure configureClosure) {
        objects.each {
            Closure closureCopy = configureClosure.clone()
            ConfigureUtil.configure(closureCopy, it)
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

    public void buildscript(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getBuildscript())
    }

    /**
     * Adds a task with the given name. This is called by the task creation DSL.
     */
    public Task task(String task) {
        tasks.add(task)
    }

    /**
     * Adds a task with the given name and configure closure. This is called by the task creation DSL.
     */
    public Task task(String task, Closure configureClosure) {
        tasks.add(task).configure(configureClosure)
    }

    /**
     * Adds a task with the given name and options. This is called by the task creation DSL.
     */
    public Task task(Map options, String task) {
        tasks.add(options + [name: task])
    }

    /**
     * Adds a task with the given name, options and configure closure. This is called by the task creation DSL.
     */
    public Task task(Map options, String task, Closure configureClosure) {
        tasks.add(options + [name: task]).configure(configureClosure)
    }

    /**
     * This is called by the task creation DSL. Need to find a cleaner way to do this...
     */
    public Object passThrough(Object object) {
        object
    }
}
