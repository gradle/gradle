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
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ModuleInternal;
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory;
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil;
import org.gradle.api.internal.artifacts.ivyservice.LocalComponentFactory;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependenciesToModuleDescriptorConverter;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetaData;
import org.gradle.internal.component.local.model.MutableLocalComponentMetaData;

import java.util.Set;

public class ResolveLocalComponentFactory implements LocalComponentFactory {
    static final String IVY_MAVEN_NAMESPACE = "http://ant.apache.org/ivy/maven";
    static final String IVY_MAVEN_NAMESPACE_PREFIX = "m";

    private final ConfigurationsToModuleDescriptorConverter configurationsToModuleDescriptorConverter;
    private final DependenciesToModuleDescriptorConverter dependenciesToModuleDescriptorConverter;
    private final ComponentIdentifierFactory componentIdentifierFactory;
    private final ConfigurationsToArtifactsConverter configurationsToArtifactsConverter;

    public ResolveLocalComponentFactory(ConfigurationsToModuleDescriptorConverter configurationsToModuleDescriptorConverter,
                                        DependenciesToModuleDescriptorConverter dependenciesToModuleDescriptorConverter,
                                        ComponentIdentifierFactory componentIdentifierFactory,
                                        ConfigurationsToArtifactsConverter configurationsToArtifactsConverter) {
        this.configurationsToModuleDescriptorConverter = configurationsToModuleDescriptorConverter;
        this.dependenciesToModuleDescriptorConverter = dependenciesToModuleDescriptorConverter;
        this.componentIdentifierFactory = componentIdentifierFactory;
        this.configurationsToArtifactsConverter = configurationsToArtifactsConverter;
    }

    public MutableLocalComponentMetaData convert(Set<? extends Configuration> configurations, ModuleInternal module) {
        assert configurations.size() > 0 : "No configurations found for module: " + module.getName() + ". Configure them or apply a plugin that does it.";
        DefaultModuleDescriptor moduleDescriptor = new DefaultModuleDescriptor(IvyUtil.createModuleRevisionId(module), module.getStatus(), null);
        moduleDescriptor.addExtraAttributeNamespace(IVY_MAVEN_NAMESPACE_PREFIX, IVY_MAVEN_NAMESPACE);
        ComponentIdentifier componentIdentifier = componentIdentifierFactory.createComponentIdentifier(module);
        DefaultLocalComponentMetaData metaData = new DefaultLocalComponentMetaData(moduleDescriptor, componentIdentifier);
        configurationsToModuleDescriptorConverter.addConfigurations(metaData, configurations);
        dependenciesToModuleDescriptorConverter.addDependencyDescriptors(metaData, configurations);
        configurationsToArtifactsConverter.addArtifacts(metaData, configurations);
        return metaData;
    }
}
