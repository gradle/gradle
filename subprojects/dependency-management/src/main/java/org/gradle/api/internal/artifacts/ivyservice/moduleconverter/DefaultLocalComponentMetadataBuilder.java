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
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.Configurations;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.LocalConfigurationMetadataBuilder;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.local.model.BuildableLocalComponentMetadata;
import org.gradle.internal.component.local.model.BuildableLocalConfigurationMetadata;
import org.gradle.internal.component.model.ComponentConfigurationIdentifier;
import org.gradle.internal.component.model.VariantResolveMetadata;

import java.util.Collection;

public class DefaultLocalComponentMetadataBuilder implements LocalComponentMetadataBuilder {
    private final LocalConfigurationMetadataBuilder configurationMetadataBuilder;

    public DefaultLocalComponentMetadataBuilder(LocalConfigurationMetadataBuilder configurationMetadataBuilder) {
        this.configurationMetadataBuilder = configurationMetadataBuilder;
    }

    @Override
    public BuildableLocalConfigurationMetadata addConfiguration(BuildableLocalComponentMetadata metaData, ConfigurationInternal configuration) {
        BuildableLocalConfigurationMetadata configurationMetadata = createConfiguration(metaData, configuration);

        metaData.addDependenciesAndExcludesForConfiguration(configuration, configurationMetadataBuilder);
        ComponentConfigurationIdentifier configurationIdentifier = new ComponentConfigurationIdentifier(metaData.getId(), configuration.getName());

        configuration.collectVariants(new ConfigurationInternal.VariantVisitor() {
            @Override
            public void visitArtifacts(Collection<? extends PublishArtifact> artifacts) {
                configurationMetadata.addArtifacts(artifacts);
            }

            @Override
            public void visitOwnVariant(DisplayName displayName, ImmutableAttributes attributes, Collection<? extends Capability> capabilities, Collection<? extends PublishArtifact> artifacts) {
                configurationMetadata.addVariant(configuration.getName(), configurationIdentifier, displayName, attributes, ImmutableCapabilities.of(capabilities), artifacts);
            }

            @Override
            public void visitChildVariant(String name, DisplayName displayName, ImmutableAttributes attributes, Collection<? extends Capability> capabilities, Collection<? extends PublishArtifact> artifacts) {
                configurationMetadata.addVariant(configuration.getName() + "-" + name, new NestedVariantIdentifier(configurationIdentifier, name), displayName, attributes, ImmutableCapabilities.of(capabilities), artifacts);
            }
        });
        return configurationMetadata;
    }

    private BuildableLocalConfigurationMetadata createConfiguration(BuildableLocalComponentMetadata metaData,
                                                                    ConfigurationInternal configuration) {
        configuration.preventFromFurtherMutation();

        ImmutableSet<String> hierarchy = Configurations.getNames(configuration.getHierarchy());
        ImmutableSet<String> extendsFrom = Configurations.getNames(configuration.getExtendsFrom());
        // Presence of capabilities is bound to the definition of a capabilities extension to the project
        ImmutableCapabilities capabilities = ImmutableCapabilities.of(Configurations.collectCapabilities(configuration, Sets.newHashSet(), Sets.newHashSet()));
        return metaData.addConfiguration(configuration.getName(),
            configuration.getDescription(),
            extendsFrom,
            hierarchy,
            configuration.isVisible(),
            configuration.isTransitive(),
            configuration.getAttributes().asImmutable(),
            configuration.isCanBeConsumed(),
            configuration.getConsumptionDeprecation(),
            configuration.isCanBeResolved(),
            capabilities);
    }

    private static class NestedVariantIdentifier implements VariantResolveMetadata.Identifier {
        private final VariantResolveMetadata.Identifier parent;
        private final String name;

        public NestedVariantIdentifier(VariantResolveMetadata.Identifier parent, String name) {
            this.parent = parent;
            this.name = name;
        }

        @Override
        public int hashCode() {
            return parent.hashCode() ^ name.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            NestedVariantIdentifier other = (NestedVariantIdentifier) obj;
            return parent.equals(other.parent) && name.equals(other.name);
        }
    }
}
