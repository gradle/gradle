/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.component;

import org.gradle.api.Incubating;
import org.gradle.api.Named;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.file.FileCollection;

import java.util.Set;

/**
 * A Variant of a {@link ConsumableComponent} which can participate in dependency resolution and
 * be published to repositories.
 *
 * <p>Variants are identified by their attributes and capabilities. They produce artifacts and may
 * have dependencies on other components.</p>
 *
 * @since 8.2
 */
@Incubating
public interface ConsumableVariant extends Named, HasAttributes {

    // TODO: Local variants backed by configurations can have multiple attribute sets.
    // Configurations support sub-variants via the ConfigurationPublications API, each of
    // which with a different attribute set. These sub-variants are selected during artifact
    // selection based on additional attributes provided by the sub-variant. How does this
    // fit into the ConsumableVariant API?

    /**
     * The files produced by this variant.
     */
    FileCollection getArtifacts();

    /**
     * This variant's dependencies. Dependencies affect the resolution graph
     * by contributing their own dependencies and dependency constraints, and contribute their
     * own artifacts to the final resolved artifact set.
     *
     * @see org.gradle.api.artifacts.dsl.DependencyHandler
     */
    Set<? extends Dependency> getDependencies();

    /**
     * This variant's dependency constraints. Dependency constraints affect the resolution
     * graph in the same manner as dependencies, but do not contribute artifacts to the
     * final resolved artifact set.
     *
     * @see org.gradle.api.artifacts.dsl.DependencyHandler
     */
    Set<? extends DependencyConstraint> getDependencyConstraints();

    /**
     * The capabilities of this variant. Capabilities describe the API or content of the
     * artifacts produced by this variant. No two variants with the same capabilities may
     * exist in a single dependency graph at the same time.
     */
    CapabilitiesMetadata getCapabilities();

}
