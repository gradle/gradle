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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.Property;

/**
 * Describes how declared dependencies of a local component should be mapped to the dependencies of the published component.
 *
 * @since 8.5
 */
@Incubating
public interface DependencyMappingDetails {
    /**
     * If true, the resolved coordinates of the dependencies will be published instead of
     * the declared coordinates. For example, if a given dependency resolves to a variant
     * of the target component that is published as an external variant, then the external
     * coordinates will be published instead of the declared coordinates.
     */
    Property<Boolean> getPublishResolvedCoordinates();

    /**
     * The configuration that is resolved in order to determine the coordinates and versions
     * of the dependencies to publish. This configuration <strong>must</strong> have the same
     * exact dependencies and dependency constraints as the variant being published, otherwise
     * the behavior of this feature is undefined.
     */
    Property<Configuration> getResolutionConfiguration();
}
