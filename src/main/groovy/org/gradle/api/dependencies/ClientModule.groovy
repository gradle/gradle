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

package org.gradle.api.dependencies

import org.apache.ivy.core.module.descriptor.*
import org.apache.ivy.core.module.id.ModuleId
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.internal.dependencies.DefaultDependencyContainer
import org.gradle.api.internal.dependencies.DependencyFactory
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.internal.dependencies.DependencyDescriptorFactory


/**
 * @author Hans Dockter
 */
// todo the setProject method of Dependency is only needed by the ProjectDependency. We need to refactor.
class ClientModule extends DefaultDependencyContainer implements Dependency {
    static final String CLIENT_MODULE_KEY = 'org.gradle.clientModule'

    String id

    Set confs

    DependencyDescriptorFactory dependencyDescriptorFactory = new DependencyDescriptorFactory()

    ClientModule() {}

    ClientModule(DependencyFactory dependencyFactory, Set confs,
                 String id, Map moduleRegistry) {
        super(dependencyFactory, [Dependency.DEFAULT_CONFIGURATION])
        this.id = id
        this.clientModuleRegistry = moduleRegistry
        this.confs = confs
    }

    DependencyDescriptor createDepencencyDescriptor() {
        DefaultDependencyDescriptor dd = dependencyDescriptorFactory.createDescriptor(id, false, true, true, confs, [],
                [(CLIENT_MODULE_KEY): id])
        addModuleDescriptors(dd.dependencyRevisionId)
        dd
    }

    void addModuleDescriptors(ModuleRevisionId moduleRevisionId) {
        DefaultModuleDescriptor moduleDescriptor = new DefaultModuleDescriptor(moduleRevisionId,
                'release', null)
        moduleDescriptor.addConfiguration(new Configuration(Dependency.DEFAULT_CONFIGURATION))
        addDependencyDescriptors(moduleDescriptor)
        moduleDescriptor.addArtifact(Dependency.DEFAULT_CONFIGURATION, new DefaultArtifact(moduleRevisionId, null, moduleRevisionId.name, 'jar', 'jar'))
        this.clientModuleRegistry[id] = moduleDescriptor
    }

    void addDependencyDescriptors(DefaultModuleDescriptor moduleDescriptor) {
        List dependencyDescriptors = dependencies.collect() {Dependency dependency ->
            dependency.createDepencencyDescriptor()
        }
        (dependencyDescriptors + this.dependencyDescriptors).each {moduleDescriptor.addDependency(it)}
    }

    public void setProject(DefaultProject project) {
        // do nothing
    }

    public void initialize() {
        // do nothing
    }

    void dependencies(List confs, Object[] dependencies) {
        if (confs != defaultConfs) {
            throw new UnsupportedOperationException("You can assign confs $confs to dependencies in a client module.")
        }
        super.dependencies(confs, dependencies)
    }

    ModuleDependency dependency(List confs, String id, Closure configureClosure = null) {
        if (confs != defaultConfs) {
            throw new UnsupportedOperationException("You can assign confs $confs to dependencies in a client module.")
        }
        super.dependency(confs, id, configureClosure)
    }

    ClientModule clientModule(List confs, String artifact, Closure configureClosure = null) {
        if (confs != defaultConfs) {
            throw new UnsupportedOperationException("You can assign confs $confs to dependencies in a client module.")
        }
        super.clientModule(confs, artifact, configureClosure)
    }


}
