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

import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependencyConstraint;

import javax.annotation.Nullable;

/**
 * Given a declared dependency to be published, determines the coordinates
 * that should be used to reference the dependency in published metadata. Dependencies
 * are resolved to component-level precision, meaning the coordinates of the resolved
 * variant's owning component are returned.
 */
public interface ComponentDependencyResolver {

    /**
     * Determines the published coordinates for an external dependency to component-level precision.
     *
     * @return null if the external dependency could not be resolved.
     */
    @Nullable
    ResolvedCoordinates resolveComponentCoordinates(ExternalDependency dependency);

    /**
     * Determines the published coordinates for a project dependency to component-level precision.
     *
     * @throws RuntimeException If the project cannot be resolved.
     */
    ResolvedCoordinates resolveComponentCoordinates(ProjectDependency dependency);

    /**
     * Determines the published coordinates for an external dependency constraint to component-level precision.
     *
     * @return null if the external dependency constraint could not be resolved.
     */
    @Nullable
    ResolvedCoordinates resolveComponentCoordinates(DependencyConstraint dependency);

    /**
     * Determines the published coordinates for a project dependency constraint to component-level precision.
     *
     * @throws RuntimeException If the project cannot be resolved.
     */
    ResolvedCoordinates resolveComponentCoordinates(DefaultProjectDependencyConstraint dependency);
}
