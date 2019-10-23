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
package org.gradle.api.artifacts.dsl;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.artifacts.DependencyConstraint;

/**
 * <p>A {@code DependencyConstraintHandler} is used to declare dependency constraints.</p>
 *
 * @since 4.5
 */
public interface DependencyConstraintHandler {
    /**
     * Adds a dependency constraint to the given configuration.
     *
     * @param configurationName The name of the configuration.
     * @param dependencyConstraintNotation the constraint
     */
    DependencyConstraint add(String configurationName, Object dependencyConstraintNotation);

    /**
     * Adds a dependency constraint to the given configuration, and configures the dependency constraint using the given closure.
     *
     * @param configurationName The name of the configuration.
     * @param dependencyNotation The dependency constraint notation
     * @param configureAction The closure to use to configure the dependency constraint.
     */
    DependencyConstraint add(String configurationName, Object dependencyNotation, Action<? super DependencyConstraint> configureAction);

    /**
     * Creates a dependency constraint without adding it to a configuration.
     *
     * @param dependencyConstraintNotation The dependency constraint notation.
     */
    DependencyConstraint create(Object dependencyConstraintNotation);

    /**
     * Creates a dependency constraint without adding it to a configuration, and configures the dependency constraint using
     * the given closure.
     *
     * @param dependencyConstraintNotation The dependency constraint notation.
     * @param configureAction The closure to use to configure the dependency.
     */
    DependencyConstraint create(Object dependencyConstraintNotation, Action<? super DependencyConstraint> configureAction);

    /**
     * Declares a constraint on an enforced platform. If the target coordinates represent multiple
     * potential components, the platform component will be selected, instead of the library.
     * An enforced platform is a platform for which the direct dependencies are forced, meaning
     * that they would override any other version found in the graph.
     *
     * @param notation the coordinates of the platform
     *
     * @since 5.0
     */
    @Incubating
    DependencyConstraint enforcedPlatform(Object notation);

    /**
     * Declares a constraint on an enforced platform. If the target coordinates represent multiple
     * potential components, the platform component will be selected, instead of the library.
     * An enforced platform is a platform for which the direct dependencies are forced, meaning
     * that they would override any other version found in the graph.
     *
     * @param notation the coordinates of the platform
     * @param configureAction the dependency configuration block
     *
     * @since 5.0
     */
    @Incubating
    DependencyConstraint enforcedPlatform(Object notation, Action<? super DependencyConstraint> configureAction);
}
