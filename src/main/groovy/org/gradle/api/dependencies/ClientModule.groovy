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
import org.gradle.api.internal.dependencies.DependencyContainer
import org.gradle.api.internal.dependencies.DependencyFactory
import org.gradle.api.internal.project.DefaultProject

/**
 * @author Hans Dockter
 */
class ClientModule extends DependencyContainer implements Dependency {
    static final String CLIENT_MODULE_KEY = 'org.gradle.clientModule'
    String artifact

    Set confs

    ClientModule() {}

    ClientModule(DependencyFactory dependencyFactory, Set confs,
                 String artifact, Map moduleDescriptorRegistry) {
        super(dependencyFactory, [Dependency.DEFAULT_CONFIGURATION])
        this.artifact = artifact
        this.clientModuleRegistry = moduleDescriptorRegistry
        this.confs = confs
    }

    DependencyDescriptor createDepencencyDescriptor() {
        addModuleDescriptors()
        DefaultDependencyDescriptor dd = new DefaultDependencyDescriptor(null,
                createModuleRevisionId([(CLIENT_MODULE_KEY): artifact]), false, true, true)
        confs.each {String conf ->
            dd.addDependencyConfiguration(conf, Dependency.DEFAULT_CONFIGURATION)
        }
        dd
    }

    void addModuleDescriptors() {
        ModuleRevisionId moduleRevisionId = createModuleRevisionId([(CLIENT_MODULE_KEY): artifact])
        DefaultModuleDescriptor moduleDescriptor = new DefaultModuleDescriptor(moduleRevisionId,
                'release', null)
        moduleDescriptor.addConfiguration(new Configuration(Dependency.DEFAULT_CONFIGURATION))
        addDependencyDescriptors(moduleDescriptor)
        moduleDescriptor.addArtifact(Dependency.DEFAULT_CONFIGURATION, new DefaultArtifact(moduleRevisionId, null, moduleRevisionId.name, 'jar', 'jar'))
        this.clientModuleRegistry[artifact] = moduleDescriptor
    }

    void addDependencyDescriptors(DefaultModuleDescriptor moduleDescriptor) {
        List dependencyDescriptors = dependencies.collect() {Dependency dependency ->
            dependency.createDepencencyDescriptor()
        }
        (dependencyDescriptors + this.dependencyDescriptors).each {moduleDescriptor.addDependency(it)}
    }

    ModuleRevisionId createModuleRevisionId(Map extraAttributes) {
        List dependencyParts = artifact.split(':')
        new ModuleRevisionId(new ModuleId(dependencyParts[0], dependencyParts[1]), dependencyParts[2], extraAttributes)
    }

    public void setProject(DefaultProject project) {
        // do nothing
    }

    public void initialize() {
        // do nothing
    }

    void addDependencies(List confs, Object[] dependencies) {
        if (confs != defaultConfs) {
            throw new UnsupportedOperationException("You can assign confs $confs to dependencies in a client module.")
        }
        super.addDependencies(confs, dependencies)
    }

    ModuleDependency addDependency(List confs, String id, Closure configureClosure = null) {
        if (confs != defaultConfs) {
            throw new UnsupportedOperationException("You can assign confs $confs to dependencies in a client module.")
        }
        super.addDependency(confs, id, configureClosure)
    }

    ClientModule addClientModule(List confs, String artifact, Closure configureClosure = null) {
        if (confs != defaultConfs) {
            throw new UnsupportedOperationException("You can assign confs $confs to dependencies in a client module.")
        }
        super.addClientModule(confs, artifact, configureClosure)
    }


}
