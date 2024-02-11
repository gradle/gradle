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

package org.gradle.api.publish.internal.mapping;

import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.publish.internal.validation.VariantWarningCollector;

import javax.annotation.Nullable;

/**
 * Given a declared dependency to be published, determines the coordinates
 * that should be used to reference the dependency in published metadata. Dependencies
 * are resolved to variant-level precision, meaning that if resolved variant
 * is published to different coordinates than the declared component, the
 * variant coordinates are returned.
 *
 * <p>Implementations of this class may fall-back to component-level precision when
 * variant-level precision is not available for a given dependency.</p>
 */
public interface VariantDependencyResolver {

    /**
     * Determines the published coordinates for an external dependency to variant-level precision.
     *
     * @return null if the external dependency could not be resolved.
     */
    @Nullable
    ResolvedCoordinates resolveVariantCoordinates(ExternalDependency dependency, VariantWarningCollector warnings);

    /**
     * Determines the published coordinates for a project dependency to variant-level precision.
     *
     * @throws RuntimeException if the project cannot be resolved.
     */
    ResolvedCoordinates resolveVariantCoordinates(ProjectDependency dependency, VariantWarningCollector warnings);

}
