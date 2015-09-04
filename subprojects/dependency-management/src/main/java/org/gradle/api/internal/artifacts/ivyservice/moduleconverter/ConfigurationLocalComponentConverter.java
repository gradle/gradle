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

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ModuleInternal;
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.LocalComponentConverter;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependenciesToModuleDescriptorConverter;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetaData;
import org.gradle.internal.component.local.model.LocalComponentMetaData;

import java.util.Set;

public class ConfigurationLocalComponentConverter implements LocalComponentConverter {
    private final ConfigurationsToModuleDescriptorConverter configurationsToModuleDescriptorConverter;
    private final DependenciesToModuleDescriptorConverter dependenciesToModuleDescriptorConverter;
    private final ComponentIdentifierFactory componentIdentifierFactory;
    private final ConfigurationsToArtifactsConverter configurationsToArtifactsConverter;

    public ConfigurationLocalComponentConverter(ConfigurationsToModuleDescriptorConverter configurationsToModuleDescriptorConverter,
                                                DependenciesToModuleDescriptorConverter dependenciesToModuleDescriptorConverter,
                                                ComponentIdentifierFactory componentIdentifierFactory,
                                                ConfigurationsToArtifactsConverter configurationsToArtifactsConverter) {
        this.configurationsToModuleDescriptorConverter = configurationsToModuleDescriptorConverter;
        this.dependenciesToModuleDescriptorConverter = dependenciesToModuleDescriptorConverter;
        this.componentIdentifierFactory = componentIdentifierFactory;
        this.configurationsToArtifactsConverter = configurationsToArtifactsConverter;
    }

    @Override
    public boolean canConvert(Object source) {
        return source instanceof ConfigurationBackedComponent || source instanceof Configuration;
    }

    @Override
    public LocalComponentMetaData convert(Object source) {
        ModuleInternal module;
        Set<? extends Configuration> configurations;
        if (source instanceof Configuration) {
            configurations = ((Configuration) source).getAll();
            module = ((ConfigurationInternal) source).getModule();
        } else {
            configurations = ((ConfigurationBackedComponent) source).getConfigurations();
            module = ((ConfigurationBackedComponent) source).getModule();
        }
        assert configurations.size() > 0 : "No configurations found for module: " + module.getName() + ". Configure them or apply a plugin that does it.";
        ComponentIdentifier componentIdentifier = componentIdentifierFactory.createComponentIdentifier(module);
        ModuleVersionIdentifier moduleVersionIdentifier = DefaultModuleVersionIdentifier.newId(module);
        DefaultLocalComponentMetaData metaData = new DefaultLocalComponentMetaData(moduleVersionIdentifier, componentIdentifier, module.getStatus());
        configurationsToModuleDescriptorConverter.addConfigurations(metaData, configurations);
        dependenciesToModuleDescriptorConverter.addDependencyDescriptors(metaData, configurations);
        configurationsToArtifactsConverter.addArtifacts(metaData, configurations);
        return metaData;
    }

}
