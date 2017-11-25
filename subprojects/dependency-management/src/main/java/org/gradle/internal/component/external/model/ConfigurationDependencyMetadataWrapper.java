/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.internal.component.external.model;

import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.List;
import java.util.Set;

public class ConfigurationDependencyMetadataWrapper extends ModuleDependencyMetadataWrapper {
    private final ConfigurationMetadata configuration;
    private final ModuleComponentIdentifier componentId;
    private final DefaultDependencyMetadata defaultDependencyMetadata;

    public ConfigurationDependencyMetadataWrapper(ConfigurationMetadata configuration, ModuleComponentIdentifier componentId, DefaultDependencyMetadata delegate) {
        super(delegate);
        this.configuration = configuration;
        this.componentId = componentId;
        this.defaultDependencyMetadata = delegate;
    }

    @Override
    public ModuleDependencyMetadata withRequestedVersion(VersionConstraint requestedVersion) {
        DefaultDependencyMetadata newDelegate = defaultDependencyMetadata.withRequestedVersion(requestedVersion);
        return new ConfigurationDependencyMetadataWrapper(configuration, componentId, newDelegate);
    }

    @Override
    public Set<ConfigurationMetadata> selectConfigurations(ImmutableAttributes consumerAttributes, ComponentResolveMetadata targetComponent, AttributesSchemaInternal consumerSchema) {
        return defaultDependencyMetadata.getMetadataForConfigurations(consumerAttributes, consumerSchema, componentId, configuration, targetComponent);
    }

    @Override
    public List<IvyArtifactName> getArtifacts() {
        return defaultDependencyMetadata.getConfigurationArtifacts(configuration);
    }

    @Override
    public List<ExcludeMetadata> getExcludes() {
        return defaultDependencyMetadata.getConfigurationExcludes(configuration.getHierarchy());
    }
}
