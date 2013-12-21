/*
 * Copyright 2007-2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies;

import org.apache.ivy.core.module.descriptor.Configuration;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.internal.artifacts.metadata.ModuleDescriptorAdapter;
import org.gradle.api.internal.artifacts.metadata.MutableModuleVersionMetaData;

import java.util.Set;

public class DefaultClientModuleMetaDataFactory implements ClientModuleMetaDataFactory {
    // Because of bidirectional dependencies we need setter injection
    private DependencyDescriptorFactory dependencyDescriptorFactory;

    public MutableModuleVersionMetaData createModuleDescriptor(ModuleRevisionId moduleRevisionId, Set<ModuleDependency> dependencies) {
        DefaultModuleDescriptor moduleDescriptor = new DefaultModuleDescriptor(moduleRevisionId,
                "release", null);
        moduleDescriptor.addConfiguration(new Configuration(Dependency.DEFAULT_CONFIGURATION));
        addDependencyDescriptors(moduleDescriptor, dependencies, dependencyDescriptorFactory);
        moduleDescriptor.addArtifact(Dependency.DEFAULT_CONFIGURATION,
                new DefaultArtifact(moduleRevisionId, null, moduleRevisionId.getName(), "jar", "jar"));
        return new ModuleDescriptorAdapter(moduleDescriptor);
    }

    private void addDependencyDescriptors(DefaultModuleDescriptor moduleDescriptor, Set<ModuleDependency> dependencies,
                                          DependencyDescriptorFactory dependencyDescriptorFactory) {
        for (ModuleDependency dependency : dependencies) {
            dependencyDescriptorFactory.addDependencyDescriptor(Dependency.DEFAULT_CONFIGURATION, moduleDescriptor,
                    dependency);
        }
    }

    public void setDependencyDescriptorFactory(DependencyDescriptorFactory dependencyDescriptorFactory) {
        this.dependencyDescriptorFactory = dependencyDescriptorFactory;
    }
}
