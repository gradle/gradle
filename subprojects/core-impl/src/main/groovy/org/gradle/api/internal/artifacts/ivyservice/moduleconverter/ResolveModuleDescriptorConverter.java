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

package org.gradle.api.internal.artifacts.ivyservice.moduleconverter;

import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Module;
import org.gradle.api.internal.artifacts.BuildableModuleVersionPublishMetaData;
import org.gradle.api.internal.artifacts.DefaultModuleVersionPublishMetaData;
import org.gradle.api.internal.artifacts.ivyservice.ModuleDescriptorConverter;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependenciesToModuleDescriptorConverter;

import java.util.Set;

/**
 * @author Hans Dockter
 */
public class ResolveModuleDescriptorConverter implements ModuleDescriptorConverter {
    private final ModuleDescriptorFactory moduleDescriptorFactory;
    private final ConfigurationsToModuleDescriptorConverter configurationsToModuleDescriptorConverter;
    private final DependenciesToModuleDescriptorConverter dependenciesToModuleDescriptorConverter;

    public ResolveModuleDescriptorConverter(ModuleDescriptorFactory moduleDescriptorFactory,
                                            ConfigurationsToModuleDescriptorConverter configurationsToModuleDescriptorConverter,
                                            DependenciesToModuleDescriptorConverter dependenciesToModuleDescriptorConverter) {
        this.moduleDescriptorFactory = moduleDescriptorFactory;
        this.configurationsToModuleDescriptorConverter = configurationsToModuleDescriptorConverter;
        this.dependenciesToModuleDescriptorConverter = dependenciesToModuleDescriptorConverter;
    }

    public BuildableModuleVersionPublishMetaData convert(Set<? extends Configuration> configurations, Module module) {
        assert configurations.size() > 0 : "No configurations found for module: " + module.getName() + ". Configure them or apply a plugin that does it.";
        DefaultModuleDescriptor moduleDescriptor = moduleDescriptorFactory.createModuleDescriptor(module);
        configurationsToModuleDescriptorConverter.addConfigurations(moduleDescriptor, configurations);
        dependenciesToModuleDescriptorConverter.addDependencyDescriptors(moduleDescriptor, configurations);
        return new DefaultModuleVersionPublishMetaData(moduleDescriptor);
    }
}
