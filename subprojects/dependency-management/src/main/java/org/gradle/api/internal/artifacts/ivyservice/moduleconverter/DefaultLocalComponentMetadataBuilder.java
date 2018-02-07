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
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.LocalConfigurationMetadataBuilder;
import org.gradle.internal.component.local.model.BuildableLocalComponentMetadata;
import org.gradle.internal.component.local.model.BuildableLocalConfigurationMetadata;

import java.util.Collection;
import java.util.Set;

public class DefaultLocalComponentMetadataBuilder implements LocalComponentMetadataBuilder {
    private final LocalConfigurationMetadataBuilder configurationMetadataBuilder;

    public DefaultLocalComponentMetadataBuilder(LocalConfigurationMetadataBuilder configurationMetadataBuilder) {
        this.configurationMetadataBuilder = configurationMetadataBuilder;
    }

    public void addConfigurations(BuildableLocalComponentMetadata metaData, Collection<? extends ConfigurationInternal> configurations) {
        for (ConfigurationInternal configuration : configurations) {
            addConfiguration(metaData, configuration);

            metaData.addDependenciesAndExcludesForConfiguration(configuration, configurationMetadataBuilder);

            OutgoingVariant outgoingVariant = configuration.convertToOutgoingVariant();
            metaData.addArtifacts(configuration.getName(), outgoingVariant.getArtifacts());
            for (OutgoingVariant variant : outgoingVariant.getChildren()) {
                metaData.addVariant(configuration.getName(), variant);
            }
        }
    }

    private BuildableLocalConfigurationMetadata addConfiguration(BuildableLocalComponentMetadata metaData, ConfigurationInternal configuration) {
        configuration.preventFromFurtherMutation();

        Set<String> hierarchy = Configurations.getNames(configuration.getHierarchy());
        Set<String> extendsFrom = Configurations.getNames(configuration.getExtendsFrom());
        return metaData.addConfiguration(configuration.getName(),
            configuration.getDescription(),
            extendsFrom,
            hierarchy,
            configuration.isVisible(),
            configuration.isTransitive(),
            configuration.getAttributes().asImmutable(),
            configuration.isCanBeConsumed(),
            configuration.isCanBeResolved());
    }

}
