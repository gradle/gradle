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

import groovy.lang.Closure;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.settings.IvySettings;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Module;
import org.gradle.api.internal.ChainingTransformer;
import org.gradle.api.internal.artifacts.ivyservice.ModuleDescriptorConverter;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependenciesToModuleDescriptorConverter;
import org.gradle.util.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class DefaultModuleDescriptorConverter implements ModuleDescriptorConverter {
    private static Logger logger = LoggerFactory.getLogger(DefaultModuleDescriptorConverter.class);

    private ChainingTransformer<DefaultModuleDescriptor> transformer
            = new ChainingTransformer<DefaultModuleDescriptor>(DefaultModuleDescriptor.class);

    private ModuleDescriptorFactory moduleDescriptorFactory = new DefaultModuleDescriptorFactory();

    private ConfigurationsToModuleDescriptorConverter configurationsToModuleDescriptorConverter = new DefaultConfigurationsToModuleDescriptorConverter();
    private DependenciesToModuleDescriptorConverter dependenciesToModuleDescriptorConverter;
    private ArtifactsToModuleDescriptorConverter artifactsToModuleDescriptorConverter = new DefaultArtifactsToModuleDescriptorConverter();

    public DefaultModuleDescriptorConverter(DependenciesToModuleDescriptorConverter dependenciesToModuleDescriptorConverter) {
        this.dependenciesToModuleDescriptorConverter = dependenciesToModuleDescriptorConverter;
    }

    public ModuleDescriptor convertForResolve(Configuration configuration, Module module, IvySettings settings) {
        Clock clock = new Clock();
        DefaultModuleDescriptor moduleDescriptor = createCommonModuleDescriptor(module, (Set<Configuration>) new LinkedHashSet(configuration.getHierarchy()), settings);
        logger.debug("Timing: Ivy convert for resolve took {}", clock.getTime());
        return transformer.transform(moduleDescriptor);
    }

    public ModuleDescriptor convertForPublish(Set<Configuration> configurations, boolean publishDescriptor, Module module, IvySettings settings) {
        assert configurations.size() > 0;
        Clock clock = new Clock();
        Set<Configuration> descriptorConfigurations = publishDescriptor ? setWithAllConfs(configurations) : configurations;
        ModuleDescriptor moduleDescriptor = createPublishModuleDescriptor(module, descriptorConfigurations, settings);
        artifactsToModuleDescriptorConverter.addArtifacts((DefaultModuleDescriptor) moduleDescriptor, descriptorConfigurations);
        logger.debug("Timing: Ivy convert for publish took {}", clock.getTime());
        return moduleDescriptor;
    }

    private Set<Configuration> setWithAllConfs(Set<Configuration> configurations) {
        return configurations.iterator().next().getAll();
    }

    private ModuleDescriptor createPublishModuleDescriptor(Module module, Set<Configuration> configurations, IvySettings ivySettings) {
        DefaultModuleDescriptor moduleDescriptor = createCommonModuleDescriptor(module, configurations, ivySettings);
        return transformer.transform(moduleDescriptor);
    }

    private DefaultModuleDescriptor createCommonModuleDescriptor(Module module, Set<Configuration> configurations, IvySettings ivySettings) {
        DefaultModuleDescriptor moduleDescriptor = moduleDescriptorFactory.createModuleDescriptor(module);
        configurationsToModuleDescriptorConverter.addConfigurations(moduleDescriptor, configurations);
        dependenciesToModuleDescriptorConverter.addDependencyDescriptors(moduleDescriptor, configurations, ivySettings);
        return moduleDescriptor;
    }

    public void addIvyTransformer(Transformer<DefaultModuleDescriptor> transformer) {
        this.transformer.add(transformer);
    }

    public void addIvyTransformer(Closure tranformer) {
        this.transformer.add(tranformer);
    }

    public ModuleDescriptorFactory getModuleDescriptorFactory() {
        return moduleDescriptorFactory;
    }

    public void setModuleDescriptorFactory(ModuleDescriptorFactory moduleDescriptorFactory) {
        this.moduleDescriptorFactory = moduleDescriptorFactory;
    }

    public ConfigurationsToModuleDescriptorConverter getConfigurationsToModuleDescriptorConverter() {
        return configurationsToModuleDescriptorConverter;
    }

    public void setConfigurationsToModuleDescriptorConverter(ConfigurationsToModuleDescriptorConverter configurationsToModuleDescriptorConverter) {
        this.configurationsToModuleDescriptorConverter = configurationsToModuleDescriptorConverter;
    }

    public DependenciesToModuleDescriptorConverter getDependenciesToModuleDescriptorConverter() {
        return dependenciesToModuleDescriptorConverter;
    }

    public void setDependenciesToModuleDescriptorConverter(DependenciesToModuleDescriptorConverter dependenciesToModuleDescriptorConverter) {
        this.dependenciesToModuleDescriptorConverter = dependenciesToModuleDescriptorConverter;
    }

    public ArtifactsToModuleDescriptorConverter getArtifactsToModuleDescriptorConverter() {
        return artifactsToModuleDescriptorConverter;
    }

    public void setArtifactsToModuleDescriptorConverter(ArtifactsToModuleDescriptorConverter artifactsToModuleDescriptorConverter) {
        this.artifactsToModuleDescriptorConverter = artifactsToModuleDescriptorConverter;
    }
}
