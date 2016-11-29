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
package org.gradle.api.internal.artifacts.ivyservice.moduleconverter;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.AttributeContainer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.component.CompositeSoftwareComponent;
import org.gradle.api.component.ConsumableSoftwareComponent;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.internal.AttributeContainerInternal;
import org.gradle.api.internal.artifacts.configurations.Configurations;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependenciesToModuleDescriptorConverter;
import org.gradle.api.internal.component.ConsumableSoftwareComponentInternal;
import org.gradle.internal.component.local.model.BuildableLocalComponentMetadata;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetadata;

import java.util.Collection;
import java.util.Set;

public class DefaultConfigurationComponentMetaDataBuilder implements ConfigurationComponentMetaDataBuilder {
    private final DependenciesToModuleDescriptorConverter dependenciesConverter;

    public DefaultConfigurationComponentMetaDataBuilder(DependenciesToModuleDescriptorConverter dependenciesConverter) {
        this.dependenciesConverter = dependenciesConverter;
    }

    public void addConfigurations(BuildableLocalComponentMetadata metaData, Collection<? extends Configuration> configurations) {
        for (Configuration configuration : configurations) {
            addConfiguration(metaData, configuration);
        }
        addDependencies(metaData, configurations);
        addArtifacts(metaData, configurations);
    }

    @Override
    public void addComponents(DefaultLocalComponentMetadata metaData, Collection<? extends SoftwareComponent> components) {
        for (SoftwareComponent component : components) {
            if (component instanceof CompositeSoftwareComponent) {
                CompositeSoftwareComponent compositeComponent = (CompositeSoftwareComponent) component;
                addComponents(metaData, compositeComponent.getChildren());
            }
            if (component instanceof ConsumableSoftwareComponent) {
                ConsumableSoftwareComponentInternal consumableComponent = (ConsumableSoftwareComponentInternal) component;
                String configurationName = consumableComponent.getConfigurationName();
                AttributeContainer attributes = consumableComponent.getMergedAttributes();
                // TODO:ADAM ensure cannot be referenced directly
                metaData.addConfiguration(configurationName, null, ImmutableSet.<String>of(), ImmutableSet.of(configurationName), true, true, attributes, true, false);
                // TODO:ADAM these are added in a different order
                dependenciesConverter.addDependencies(metaData, configurationName, attributes, consumableComponent.getDependencies());
                // TODO:ADAM these are added in a different order
                metaData.addArtifacts(configurationName, consumableComponent.getArtifacts());
            }
        }
    }

    private void addConfiguration(BuildableLocalComponentMetadata metaData, Configuration configuration) {
        Set<String> hierarchy = Configurations.getNames(configuration.getHierarchy());
        Set<String> extendsFrom = Configurations.getNames(configuration.getExtendsFrom());
        metaData.addConfiguration(configuration.getName(),
            configuration.getDescription(),
            extendsFrom,
            hierarchy,
            configuration.isVisible(),
            configuration.isTransitive(),
            ((AttributeContainerInternal)configuration.getAttributes()).asImmutable(),
            configuration.isCanBeConsumed(),
            configuration.isCanBeResolved());
    }

    private void addDependencies(BuildableLocalComponentMetadata metaData, Collection<? extends Configuration> configurations) {
        dependenciesConverter.addDependencyDescriptors(metaData, configurations);
    }

    private void addArtifacts(BuildableLocalComponentMetadata metaData, Collection<? extends Configuration> configurations) {
        for (Configuration configuration : configurations) {
            metaData.addArtifacts(configuration.getName(), configuration.getArtifacts());
        }
    }
}
