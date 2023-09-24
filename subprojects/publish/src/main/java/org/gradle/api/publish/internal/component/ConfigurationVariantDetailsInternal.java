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

package org.gradle.api.publish.internal.component;

import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.component.ConfigurationVariantDetails;
import org.gradle.api.provider.Property;

/**
 * Internal counterpart to {@link org.gradle.api.component.ConfigurationVariantDetails}.
 */
public interface ConfigurationVariantDetailsInternal extends ConfigurationVariantDetails {

    /**
     * Configure the dependency mapping options for this configuration variant.
     *
     * <p>This method should eventually be moved to {@link ConfigurationVariantDetails} once
     * dependency mapping is stabilized.</p>
     *
     * @param action The action to execute against this variant's dependency mapping details.
     */
    void dependencyMapping(Action<? super DependencyMappingDetails> action);

    /**
     * Describes how declared dependencies of a local component should be mapped to the dependencies of the published component.
     *
     * <p>This interface should eventually be moved to its own file once dependency mapping is stabilized.</p>
     */
    interface DependencyMappingDetails {
        /**
         * If true, the resolved coordinates of the dependencies will be published instead of
         * the declared coordinates. For example, if a given dependency resolves to a variant
         * of the target component that is published as an external variant, then the external
         * coordinates will be published instead of the declared coordinates.
         */
        Property<Boolean> getPublishResolvedCoordinates();

        /**
         * Sets the configuration that is resolved in order to determine the coordinates and versions
         * of the dependencies to publish. This configuration <strong>must</strong> have the same
         * exact dependencies and dependency constraints as the variant being published, otherwise
         * the behavior of this feature is undefined.
         */
        void fromResolutionOf(Configuration configuration);
    }

}
