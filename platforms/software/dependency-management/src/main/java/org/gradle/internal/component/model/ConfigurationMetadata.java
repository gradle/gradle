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

package org.gradle.internal.component.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.external.model.maven.MavenDependencyDescriptor;

import java.util.List;
import java.util.Set;

/**
 * <p>Note that this type is being replaced by several other interfaces that separate out the data and state required at various stages of dependency resolution.
 * You should try to use those interfaces instead of using this interface or introduce a new interface that provides a view over this type but exposes only the
 * data required.
 * </p>
 *
 * @see VariantGraphResolveMetadata
 * @see ConfigurationGraphResolveMetadata
 */
public interface ConfigurationMetadata extends VariantArtifactGraphResolveMetadata, HasAttributes {
    /**
     * The set of configurations that this configuration extends. Includes this configuration.
     *
     * It would be good to remove this from the API, as consumers of this interface generally have no need
     * for this information. However it _is_ currently used by {@link MavenDependencyDescriptor#selectLegacyConfigurations}
     * to determine if the target 'runtime' configuration includes the target 'compile' configuration.
     */
    ImmutableSet<String> getHierarchy();

    String getName();

    DisplayName asDescribable();

    /**
     * Attributes are immutable on ConfigurationMetadata
     */
    @Override
    ImmutableAttributes getAttributes();

    /**
     * Returns the dependencies that apply to this configuration.
     *
     * If the implementation supports {@link DependencyMetadataRules}, this method
     * is responsible for lazily applying the rules the first time it is called.
     */
    List<? extends DependencyMetadata> getDependencies();

    /**
     * Returns the artifacts associated with this configuration, if known.
     */
    ImmutableList<? extends ComponentArtifactMetadata> getArtifacts();

    /**
     * Returns the variants of this configuration. Should include at least one value. Exactly one variant must be selected and the artifacts of that variant used.
     */
    Set<? extends VariantResolveMetadata> getVariants();

    /**
     * Returns the exclusions to apply to this configuration:
     * - Module exclusions apply to all outgoing dependencies from this configuration
     * - Artifact exclusions apply to artifacts obtained from this configuration
     */
    ImmutableList<ExcludeMetadata> getExcludes();

    boolean isTransitive();

    boolean isVisible();

    boolean isCanBeConsumed();

    /**
     * Find the component artifact with the given IvyArtifactName, creating a new one if none matches.
     *
     * This is used to create a ComponentArtifactMetadata from an artifact declared as part of a dependency.
     * The reason to do this lookup is that for a local component artifact, the file is part of the artifact metadata.
     * (For external module components, we just instantiate a new artifact metadata).
     */
    ComponentArtifactMetadata artifact(IvyArtifactName artifact);

    ImmutableCapabilities getCapabilities();

    boolean isExternalVariant();
}
