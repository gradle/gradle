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
import org.gradle.api.internal.artifacts.ivyservice.IvyFileConverter;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependenciesToModuleDescriptorConverter;
import org.gradle.util.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * @author Hans Dockter
 */
public class DefaultIvyFileConverter extends AbstractModuleDescriptorConverter implements IvyFileConverter {
    private static Logger logger = LoggerFactory.getLogger(DefaultIvyFileConverter.class);

    private ChainingTransformer<DefaultModuleDescriptor> transformer
            = new ChainingTransformer<DefaultModuleDescriptor>(DefaultModuleDescriptor.class);

    private ArtifactsToModuleDescriptorConverter artifactsToModuleDescriptorConverter;

    public DefaultIvyFileConverter(ModuleDescriptorFactory moduleDescriptorFactory,
                                            ConfigurationsToModuleDescriptorConverter configurationsToModuleDescriptorConverter,
                                            DependenciesToModuleDescriptorConverter dependenciesToModuleDescriptorConverter,
                                            ArtifactsToModuleDescriptorConverter artifactsToModuleDescriptorConverter) {
        super(moduleDescriptorFactory, configurationsToModuleDescriptorConverter, dependenciesToModuleDescriptorConverter);
        this.artifactsToModuleDescriptorConverter = artifactsToModuleDescriptorConverter;
    }

    public ModuleDescriptor convert(Set<Configuration> configurations, Module module, IvySettings settings) {
        assert configurations.size() > 0;
        Clock clock = new Clock();
        Set<Configuration> allConfigurations = getAll(configurations);
        DefaultModuleDescriptor moduleDescriptor = createCommonModuleDescriptor(module, allConfigurations, settings);
        artifactsToModuleDescriptorConverter.addArtifacts(moduleDescriptor, allConfigurations);
        logger.debug("Timing: Ivy convert for publish took {}", clock.getTime());
        return transformer.transform(moduleDescriptor);
    }

    private Set<Configuration> getAll(Set<Configuration> configurations) {
        return configurations.iterator().next().getAll();
    }

    public ArtifactsToModuleDescriptorConverter getArtifactsToModuleDescriptorConverter() {
        return artifactsToModuleDescriptorConverter;
    }

    public void setArtifactsToModuleDescriptorConverter(ArtifactsToModuleDescriptorConverter artifactsToModuleDescriptorConverter) {
        this.artifactsToModuleDescriptorConverter = artifactsToModuleDescriptorConverter;
    }

    public void addIvyTransformer(Transformer<DefaultModuleDescriptor> transformer) {
        this.transformer.add(transformer);
    }

    public void addIvyTransformer(Closure tranformer) {
        this.transformer.add(tranformer);
    }
}