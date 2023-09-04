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
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.publish.internal.validation.VariantWarningCollector;

import javax.annotation.Nullable;

/**
 * Given declared dependencies and dependency constraints, determines the coordinates
 * that should be used to reference the dependency in published metadata. Instances
 * of this interface are scoped to a given published variant, as each variant may
 * have different rules for resolving published dependencies.
 *
 * <p>This resolver can resolve dependencies to either component or variant precision.
 * When resolving to component precision, the returned coordinates will reference the
 * root coordinates of a multi-coordinate publication. When resolving to variant precision,
 * the returned coordinates will reference the child coordinates of the variant selected by
 * the resolved dependency.</p>
 */
public interface VariantDependencyResolver {

    /**
     * Determines the published coordinates for a dependency to variant-level precision.
     *
     * @throws RuntimeException If {@code dependency} is a project dependency and the project cannot be resolved.
     */
    Coordinates resolveVariantCoordinates(ModuleDependency dependency, VariantWarningCollector warnings);

    /**
     * Determines the published coordinates for a dependency constraint to variant-level precision.
     *
     * @throws RuntimeException If {@code dependency} is a project dependency constraint and the project cannot be resolved.
     */
    Coordinates resolveVariantCoordinates(DependencyConstraint dependency, VariantWarningCollector warnings);

    /**
     * Determines the published coordinates for a dependency to component-level precision.
     *
     * @throws RuntimeException If {@code dependency} is a project dependency and the project cannot be resolved.
     */
    Coordinates resolveComponentCoordinates(ModuleDependency dependency);

    /**
     * Determines the published coordinates for a dependency constraint to component-level precision.
     *
     * @throws RuntimeException If {@code dependency} is a project dependency constraint and the project cannot be resolved.
     */
    Coordinates resolveComponentCoordinates(DependencyConstraint dependency);

    /**
     * Similar to {@link ModuleVersionIdentifier}, but allows a null version.
     */
    interface Coordinates {
        String getGroup();
        String getName();
        @Nullable
        String getVersion();

        static Coordinates create(String group, String name, @Nullable String version) {
            return new Coordinates() {
                @Override
                public String getGroup() {
                    return group;
                }

                @Override
                public String getName() {
                    return name;
                }

                @Override
                public String getVersion() {
                    return version;
                }
            };
        }

        static Coordinates from(ModuleVersionIdentifier identifier) {
            return new Coordinates() {
                @Override
                public String getGroup() {
                    return identifier.getGroup();
                }

                @Override
                public String getName() {
                    return identifier.getName();
                }

                @Override
                public String getVersion() {
                    return identifier.getVersion();
                }
            };
        }
    }
}
