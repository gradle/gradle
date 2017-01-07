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

import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.Configurations;
import org.gradle.api.internal.artifacts.configurations.OutgoingVariant;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependenciesToModuleDescriptorConverter;
import org.gradle.internal.component.local.model.BuildableLocalComponentMetadata;

import java.util.Collection;
import java.util.Set;

public class DefaultConfigurationComponentMetaDataBuilder implements ConfigurationComponentMetaDataBuilder {
    private final DependenciesToModuleDescriptorConverter dependenciesConverter;

    public DefaultConfigurationComponentMetaDataBuilder(DependenciesToModuleDescriptorConverter dependenciesConverter) {
        this.dependenciesConverter = dependenciesConverter;
    }

    public void addConfigurations(BuildableLocalComponentMetadata metaData, Collection<? extends ConfigurationInternal> configurations) {
        for (ConfigurationInternal configuration : configurations) {
            addConfiguration(metaData, configuration);
            dependenciesConverter.addDependencyDescriptors(metaData, configuration);
            OutgoingVariant outgoingVariant = configuration.convertToOutgoingVariant();
            metaData.addArtifacts(configuration.getName(), outgoingVariant.getArtifacts());
            for (OutgoingVariant variant : outgoingVariant.getChildren()) {
                metaData.addVariant(configuration.getName(), variant);
            }
        }
    }

    private void addConfiguration(BuildableLocalComponentMetadata metaData, ConfigurationInternal configuration) {
        configuration.lockAttributes();
        Set<String> hierarchy = Configurations.getNames(configuration.getHierarchy());
        Set<String> extendsFrom = Configurations.getNames(configuration.getExtendsFrom());
        metaData.addConfiguration(configuration.getName(),
            configuration.getDescription(),
            extendsFrom,
            hierarchy,
            configuration.isVisible(),
            configuration.isTransitive(),
            configuration.getAttributes(),
            configuration.isCanBeConsumed(),
            configuration.isCanBeResolved());
    }

}
