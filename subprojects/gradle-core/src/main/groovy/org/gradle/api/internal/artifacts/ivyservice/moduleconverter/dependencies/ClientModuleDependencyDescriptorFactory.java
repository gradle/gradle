/*
 * Copyright 2009 the original author or authors.
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

import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ClientModule;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.ExcludeRuleConverter;
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil;
import org.gradle.util.WrapUtil;

import java.util.Map;

/**
 * @author Hans Dockter
*/
public class ClientModuleDependencyDescriptorFactory extends AbstractDependencyDescriptorFactoryInternal {
    private ModuleDescriptorFactoryForClientModule moduleDescriptorFactoryForClientModule;
    private Map<String, ModuleDescriptor> clientModuleRegistry;

    public ClientModuleDependencyDescriptorFactory(ExcludeRuleConverter excludeRuleConverter, ModuleDescriptorFactoryForClientModule moduleDescriptorFactoryForClientModule, Map<String, ModuleDescriptor> clientModuleRegistry) {
        super(excludeRuleConverter);
        this.moduleDescriptorFactoryForClientModule = moduleDescriptorFactoryForClientModule;
        this.clientModuleRegistry = clientModuleRegistry;
    }

    public ModuleRevisionId createModuleRevisionId(ModuleDependency dependency) {
        return IvyUtil.createModuleRevisionId(dependency,
                WrapUtil.toMap(getClientModule(dependency).CLIENT_MODULE_KEY, getClientModule(dependency).getId()));
    }

    public DependencyDescriptor createDependencyDescriptor(ModuleDependency dependency, String configuration, ModuleDescriptor parent,
                                                           ModuleRevisionId moduleRevisionId) {
        DefaultDependencyDescriptor dependencyDescriptor = new DefaultDependencyDescriptor(parent,
                moduleRevisionId, getClientModule(dependency).isForce(),
                false, getClientModule(dependency).isTransitive());
        addExcludesArtifactsAndDependencies(configuration, getClientModule(dependency), dependencyDescriptor);

        ModuleDescriptor moduleDescriptor = moduleDescriptorFactoryForClientModule.createModuleDescriptor(
                dependencyDescriptor.getDependencyRevisionId(), getClientModule(dependency).getDependencies());
        clientModuleRegistry.put(getClientModule(dependency).getId(), moduleDescriptor);

        return dependencyDescriptor;
    }

    private ClientModule getClientModule(ModuleDependency dependency) {
        return (ClientModule) dependency;
    }

    public boolean canConvert(ModuleDependency dependency) {
        return dependency instanceof ClientModule;
    }
}
