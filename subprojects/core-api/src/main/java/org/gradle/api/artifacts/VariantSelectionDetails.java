/*
 * Copyright 2020 the original author or authors.
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
import org.gradle.api.Incubating;
import org.gradle.api.attributes.AttributeContainer;

/**
 * Allows configuring the variant-aware selection aspects of a specific
 * dependency. This includes the ability to substitute a dependency on
 * a platform with another platform, or substitute a dependency without
 * attributes with a dependency with attributes.
 *
 * @since 6.6
 */
@Incubating
public interface VariantSelectionDetails {
    /**
     * Selects the platform variant of a component
     */
    void platform();

    /**
     * Selects the enforced platform variant of a component
     */
    void enforcedPlatform();

    /**
     * Selects the library variant of a component
     */
    void library();

    /**
     * Replaces the provided selector attributes with the attributes configured
     * via the configuration action.
     * @param configurationAction the configuration action
     */
    void attributes(Action<? super AttributeContainer> configurationAction);

    /**
     * Replaces the provided selector capabilities with the capabilities configured
     * via the configuration action.
     * @param configurationAction the configuration action
     */
    void capabilities(Action<? super ModuleDependencyCapabilitiesHandler> configurationAction);

}
