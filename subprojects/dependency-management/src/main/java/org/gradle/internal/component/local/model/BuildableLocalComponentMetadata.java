/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.internal.component.local.model;

import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.OutgoingVariant;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.LocalConfigurationMetadataBuilder;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.model.ImmutableCapabilities;

import java.util.Set;

public interface BuildableLocalComponentMetadata {
    /**
     * Returns the identifier for this component.
     */
    ComponentIdentifier getId();

    /**
     * Adds some artifacts to this component. Artifacts are attached to the given configuration and each of its children. These are used only for publishing.
     */
    void addArtifacts(String configuration, Iterable<? extends PublishArtifact> artifacts);

    /**
     * Adds a variant to this component, extending from the given configuration. Every configuration should include at least one variant.
     */
    void addVariant(String configuration, OutgoingVariant variant);

    /**
     * Adds a configuration to this component.
     * @param hierarchy Must include name
     * @param attributes the attributes of the configuration.
     */
    BuildableLocalConfigurationMetadata addConfiguration(String name, String description, Set<String> extendsFrom, Set<String> hierarchy, boolean visible, boolean transitive, ImmutableAttributes attributes, boolean canBeConsumed, boolean canBeResolved, ImmutableCapabilities capabilities, boolean canBeLocked);

    /**
     * Provides a backing configuration instance from which dependencies and excludes will be sourced.
     * @param configuration The configuration instance that provides dependencies and excludes
     * @param localConfigurationMetadataBuilder A builder for translating Configuration to LocalConfigurationMetadata
     */
    void addDependenciesAndExcludesForConfiguration(ConfigurationInternal configuration, LocalConfigurationMetadataBuilder localConfigurationMetadataBuilder);
}
