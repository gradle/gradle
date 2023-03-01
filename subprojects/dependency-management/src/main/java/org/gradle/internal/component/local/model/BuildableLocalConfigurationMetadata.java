/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;
import org.gradle.internal.component.model.VariantResolveMetadata;

import java.util.Collection;

public interface BuildableLocalConfigurationMetadata {
    /**
     * Returns the identifier for the component that owns this configuration.
     */
    ComponentIdentifier getComponentId();

    /**
     * Adds a dependency to this configuration.
     */
    void addDependency(LocalOriginDependencyMetadata dependency);

    /**
     * Adds an exclude rule to this configuration.
     */
    void addExclude(ExcludeMetadata exclude);

    /**
     * Adds some file dependencies to this configuration.
     *
     * These files should be treated as regular dependencies, however they are currently treated separately as a migration step.
     */
    void addFiles(LocalFileDependencyMetadata files);

    /**
     * Adds some artifacts to this configuration. Artifacts are attached to the this configuration and each of its children.
     */
    void addArtifacts(Collection<? extends PublishArtifact> artifacts);

    /**
     * Adds a variant to this component, extending from the given configuration. Every configuration should include at least one variant.
     */
    void addVariant(String name, VariantResolveMetadata.Identifier identifier, DisplayName displayName, ImmutableAttributes attributes, ImmutableCapabilities capabilities, Collection<? extends PublishArtifact> artifacts);
}
