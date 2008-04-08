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

package org.gradle.api.internal.dependencies

import org.apache.ivy.core.module.descriptor.Configuration
import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.gradle.api.Project
import org.gradle.api.dependencies.ClientModule
import org.gradle.api.dependencies.ModuleDependency
import org.gradle.util.GradleUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author Hans Dockter
 */
class DependencyContainer {
    static Logger logger = LoggerFactory.getLogger(DependencyContainer)

    Map clientModuleRegistry = [:]

    List defaultConfs = []

    /**
     * A list of Gradle Dependency objects.
     */
    List dependencies = []

    /**
     * A list for passing directly instances of Ivy DependencyDescriptor objects.
     */
    List dependencyDescriptors = []

    DependencyFactory dependencyFactory

    Project project

    DependencyContainer() {
    }

    DependencyContainer(DependencyFactory dependencyFactory, List defaultConfs) {
        // todo: When groovy bug is fixed, uncomment it
//        assert existingConfigurations
        this.dependencyFactory = dependencyFactory
        // todo: When groovy bug is fixed, pass it via constructor
//        this.existingConfigurations = existingConfigurations
        this.defaultConfs = defaultConfs
    }

    void addDependencies(List confs, Object[] dependencies) {
        (dependencies as List).flatten().each {
            this.dependencies << dependencyFactory.createDependency(confs as Set, it, project)
        }
    }

    void addDependencies(Object[] dependencies) {
        addDependencies(defaultConfs, dependencies)
    }

    void addDependencyDescriptors(DependencyDescriptor[] dependencyDescriptors) {
        this.dependencyDescriptors.addAll(dependencyDescriptors as List)
    }

    ModuleDependency addDependency(List confs, String id, Closure configureClosure = null) {
        def dependency = dependencyFactory.createDependency(confs as Set, id, project)
        dependencies << dependency
        GradleUtil.configure(configureClosure, dependency)
        dependency
    }

    ModuleDependency addDependency(String id, Closure configureClosure = null) {
        addDependency(defaultConfs, id, configureClosure)
    }

    ClientModule addClientModule(List confs, String artifact, Closure configureClosure = null) {
        // todo: We might better have a client module factory here
        ClientModule clientModule = new ClientModule(dependencyFactory, confs as Set, artifact, clientModuleRegistry)
        dependencies << clientModule
        GradleUtil.configure(configureClosure, clientModule)
        clientModule
    }

    ClientModule addClientModule(String artifact, Closure configureClosure = null) {
        addClientModule(defaultConfs, artifact, configureClosure)
    }


    void addConfiguration(Configuration configuration) {
        configurations[configuration.name] = configuration
    }

    void addConfiguration(String configuration) {
        configurations[configuration] = new Configuration(configuration)
    }

    def configure(Closure closure) {
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.delegate = this
        closure()
        this
    }

}
