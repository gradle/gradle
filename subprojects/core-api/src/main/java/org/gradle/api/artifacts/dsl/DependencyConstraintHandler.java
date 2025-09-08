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
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * <p>A {@code DependencyConstraintHandler} is used to declare dependency constraints.</p>
 *
 * <h2>Dependency constraint notations</h2>
 *
 * <p>There are several supported dependency constraint notations. These are described below. For each dependency constraint declared this
 * way, a {@link DependencyConstraint} object is created. You can use this object to query or further configure the
 * dependency constraint.</p>
 *
 * <p>You can also always add instances of {@link DependencyConstraint} directly:</p>
 *
 * <code><i>configurationName</i>(&lt;instance&gt;)</code>
 *
 * <p>Dependency constraints can also be declared with a {@link org.gradle.api.provider.Provider} that provides any of the other supported dependency constraint notations.</p>
 *
 * <h3>External dependencies</h3>
 *
 * Module dependencies are declared using single-string notation, where each coordinate is separated by a colon.
 * All properties, except the name, are optional.
 *
 * <code><i>configurationName</i>("<i>group</i>:<i>name</i>:<i>version</i>")</code>
 *
 * <pre class='autoTested'>
 * plugins {
 *     id("java-library")
 * }
 *
 * dependencies {
 *     constraints {
 *         // Declaring constraints on module components
 *         // Coordinates are separated by a single colon -- group:name:version
 *         implementation("org.apache.commons:commons-lang3:3.17.0")
 *         testImplementation("org.mockito:mockito-core:5.18.0")
 *     }
 * }
 * </pre>
 *
 * <h3>Project dependencies</h3>
 *
 * <p>To add a project dependency constraint, you use the following notation:
 * <p><code><i>configurationName</i>(project(":some-project"))</code>
 *
 * @since 4.5
 */
@ServiceScope(Scope.Project.class)
public interface DependencyConstraintHandler {
    /**
     * Adds a dependency constraint to the given configuration.
     *
     * @param configurationName The name of the configuration.
     * @param dependencyNotation The dependency constraint notation
     */
    DependencyConstraint add(String configurationName, Object dependencyNotation);

    /**
     * Adds a dependency constraint to the given configuration, and configures the dependency constraint using the given closure.
     *
     * @param configurationName The name of the configuration.
     * @param dependencyNotation The dependency constraint notation
     * @param configureAction The closure to use to configure the dependency constraint.
     */
    DependencyConstraint add(String configurationName, Object dependencyNotation, Action<? super DependencyConstraint> configureAction);

    /**
     * Adds a dependency constraint provider to the given configuration.
     *
     * @param configurationName The name of the configuration.
     * @param dependencyNotation The dependency constraint notation provider, in one of the notations described above.
     *
     * @since 8.12
     */
    <T> void addProvider(String configurationName, Provider<T> dependencyNotation);

    /**
     * Adds a dependency constraint provider to the given configuration, eventually configures the dependency constraint using the given action.
     *
     * @param configurationName The name of the configuration.
     * @param dependencyNotation The dependency constraint notation provider, in one of the notations described above.
     * @param configureAction The action to use to configure the dependency constraint.
     *
     * @since 8.12
     */
    <T> void addProvider(String configurationName, Provider<T> dependencyNotation, Action<? super DependencyConstraint> configureAction);

    /**
     * Adds a dependency constraint provider to the given configuration.
     *
     * @param configurationName The name of the configuration.
     * @param dependencyNotation The dependency constraint notation provider, in one of the notations described above.
     *
     * @since 8.12
     */
    <T> void addProviderConvertible(String configurationName, ProviderConvertible<T> dependencyNotation);

    /**
     * Adds a dependency constraint provider to the given configuration, eventually configures the dependency constraint using the given action.
     *
     * @param configurationName The name of the configuration.
     * @param dependencyNotation The dependency constraint notation provider, in one of the notations described above.
     * @param configureAction The action to use to configure the dependency constraint.
     *
     * @since 8.12
     */
    <T> void addProviderConvertible(String configurationName, ProviderConvertible<T> dependencyNotation, Action<? super DependencyConstraint> configureAction);

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
    DependencyConstraint enforcedPlatform(Object notation, Action<? super DependencyConstraint> configureAction);
}
