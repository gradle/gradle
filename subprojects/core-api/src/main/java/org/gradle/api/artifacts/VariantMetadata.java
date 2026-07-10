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

package org.gradle.api.artifacts;

import org.gradle.api.Action;
import org.gradle.api.attributes.HasConfigurableAttributes;
import org.gradle.api.capabilities.MutableCapabilitiesMetadata;

/**
 * Represents the metadata of one variant of a component, see {@link ComponentMetadataDetails#withVariant(String, Action)}.
 *
 * @since 4.4
 */
public interface VariantMetadata extends HasConfigurableAttributes<VariantMetadata> {

    /**
     * Register a rule that modifies the dependencies of this variant.
     *
     * @param action the action that performs the dependencies adjustment
     */
    void withDependencies(Action<? super DirectDependenciesMetadata> action);

    /**
     * Register a rule that modifies the dependency constraints of this variant.
     *
     * @param action the action that performs the dependency constraints adjustment
     * @since 4.5
     */
    void withDependencyConstraints(Action<? super DependencyConstraintsMetadata> action);

    /**
     * Register a rule that modifies the capabilities of this variant.
     *
     * @param action the action that performs the capabilities adjustment
     * @since 4.7
     */
    void withCapabilities(Action<? super MutableCapabilitiesMetadata> action);

    /**
     * Register a rule that modifies the artifacts of this variant.
     *
     * @param action the action that performs the files adjustment
     * @since 6.0
     */
    void withFiles(Action<? super MutableVariantFilesMetadata> action);
}
