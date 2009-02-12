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
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.DependencyManager;
import org.gradle.api.Project;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.ChainingTransformer;
import org.gradle.api.internal.artifacts.ArtifactContainer;
import org.gradle.api.internal.artifacts.ConfigurationContainer;
import org.gradle.api.internal.artifacts.DependencyContainerInternal;
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil;
import org.gradle.api.internal.artifacts.ivyservice.ModuleDescriptorConverter;
import org.gradle.api.specs.Spec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * @author Hans Dockter
 */
public class DefaultModuleDescriptorConverter implements ModuleDescriptorConverter {
    private static Logger logger = LoggerFactory.getLogger(DefaultModuleDescriptorConverter.class);

    private ChainingTransformer<DefaultModuleDescriptor> transformer
            = new ChainingTransformer<DefaultModuleDescriptor>(DefaultModuleDescriptor.class);

    private ModuleDescriptorFactory moduleDescriptorFactory;

    private ConfigurationsToModuleDescriptorConverter configurationsToModuleDescriptorConverter;
    private DependenciesToModuleDescriptorConverter dependenciesToModuleDescriptorConverter;
    private ArtifactsToModuleDescriptorConverter artifactsToModuleDescriptorConverter;

    public DefaultModuleDescriptorConverter(ModuleDescriptorFactory moduleDescriptorFactory,
                                            ConfigurationsToModuleDescriptorConverter configurationsToModuleDescriptorConverter,
                                            DependenciesToModuleDescriptorConverter dependenciesToModuleDescriptorConverter,
                                            ArtifactsToModuleDescriptorConverter artifactsToModuleDescriptorConverter) {
        this.moduleDescriptorFactory = moduleDescriptorFactory;
        this.configurationsToModuleDescriptorConverter = configurationsToModuleDescriptorConverter;
        this.dependenciesToModuleDescriptorConverter = dependenciesToModuleDescriptorConverter;
        this.artifactsToModuleDescriptorConverter = artifactsToModuleDescriptorConverter;
    }

    public ModuleDescriptor convert(Map<String, Boolean> transitiveOverride, ConfigurationContainer configurationContainer, Spec<Configuration> configurationSpec,
                                    DependencyContainerInternal dependencyContainer, Spec<Dependency> dependencySpec,                                
                                    ArtifactContainer artifactContainer, Spec<PublishArtifact> artifactSpec) {
        String status = getStatus(dependencyContainer.getProject());
        ModuleRevisionId moduleRevisionId = IvyUtil.createModuleRevisionId(dependencyContainer.getProject());
        DefaultModuleDescriptor moduleDescriptor = moduleDescriptorFactory.createModuleDescriptor(
                moduleRevisionId,
                status,
                null);
        configurationsToModuleDescriptorConverter.addConfigurations(moduleDescriptor, configurationContainer, configurationSpec, transitiveOverride);
        dependenciesToModuleDescriptorConverter.addDependencyDescriptors(moduleDescriptor, dependencyContainer, dependencySpec);
        artifactsToModuleDescriptorConverter.addArtifacts(moduleDescriptor, artifactContainer, artifactSpec);
        return transformer.transform(moduleDescriptor);
    }

    private String getStatus(Project project) {
        String status = DependencyManager.DEFAULT_STATUS;
        if (project.hasProperty("status")) {
            status = (String) project.property("status");
        }
        return status;
    }

    public void addIvyTransformer(Transformer<DefaultModuleDescriptor> transformer) {
        this.transformer.add(transformer);
    }

    public void addIvyTransformer(Closure tranformer) {
        this.transformer.add(tranformer);
    }
}
