/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.kotlin.dsl

import org.gradle.api.Incubating
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.dsl.DependencyConstraintHandler
import org.gradle.kotlin.dsl.support.delegates.DependencyConstraintHandlerDelegate


/**
 * Receiver for `dependencies.constraints` block providing convenient utilities for configuring dependency constraints.
 *
 * @see [DependencyConstraintHandler]
 * @since 5.0
 */
class DependencyConstraintHandlerScope
private constructor(
    val constraints: DependencyConstraintHandler
) : DependencyConstraintHandlerDelegate() {

    companion object {
        fun of(constraints: DependencyConstraintHandler) =
            DependencyConstraintHandlerScope(constraints)
    }

    override val delegate: DependencyConstraintHandler
        get() = constraints

    /**
     * Adds a dependency constraint to the given configuration.
     *
     * @param dependencyConstraintNotation notation for the dependency constraint to be added.
     * @return The dependency constraint.
     * @see [DependencyConstraintHandler.add]
     */
    operator fun String.invoke(dependencyConstraintNotation: Any): DependencyConstraint? =
        constraints.add(this, dependencyConstraintNotation)

    /**
     * Adds a dependency constraint to the given configuration.
     *
     * @param dependencyConstraintNotation notation for the dependency constraint to be added.
     * @param configuration expression to use to configure the dependency constraint.
     * @return The dependency constraint.
     * @see [DependencyConstraintHandler.add]
     */
    operator fun String.invoke(dependencyConstraintNotation: String, configuration: DependencyConstraint.() -> Unit): DependencyConstraint =
        constraints.add(this, dependencyConstraintNotation, configuration)

    /**
     * Adds a dependency constraint to the given configuration.
     *
     * @param dependencyConstraintNotation notation for the dependency constraint to be added.
     * @return The dependency constraint.
     * @see [DependencyConstraintHandler.add]
     * @since 8.3
     */
    @Incubating
    operator fun NamedDomainObjectProvider<Configuration>.invoke(dependencyConstraintNotation: Any): DependencyConstraint? =
        constraints.add(name, dependencyConstraintNotation)

    /**
     * Adds a dependency constraint to the given configuration.
     *
     * @param dependencyConstraintNotation notation for the dependency constraint to be added.
     * @param configuration expression to use to configure the dependency constraint.
     * @return The dependency constraint.
     * @see [DependencyConstraintHandler.add]
     * @since 8.3
     */
    @Incubating
    operator fun NamedDomainObjectProvider<Configuration>.invoke(dependencyConstraintNotation: String, configuration: DependencyConstraint.() -> Unit): DependencyConstraint? =
        constraints.add(name, dependencyConstraintNotation, configuration)

    /**
     * Adds a dependency constraint to the given configuration.
     *
     * @param dependencyConstraintNotation notation for the dependency constraint to be added.
     * @return The dependency constraint.
     * @see [DependencyConstraintHandler.add]
     */
    operator fun Configuration.invoke(dependencyConstraintNotation: Any): DependencyConstraint? =
        add(name, dependencyConstraintNotation)

    /**
     * Adds a dependency constraint to the given configuration.
     *
     * @param dependencyConstraintNotation notation for the dependency constraint to be added.
     * @param configuration expression to use to configure the dependency constraint.
     * @return The dependency constraint.
     * @see [DependencyConstraintHandler.add]
     */
    operator fun Configuration.invoke(dependencyConstraintNotation: String, configuration: DependencyConstraint.() -> Unit): DependencyConstraint =
        add(name, dependencyConstraintNotation, configuration)

    /**
     * Configures the dependency constraints.
     */
    inline operator fun invoke(configuration: DependencyConstraintHandlerScope.() -> Unit) =
        this.configuration()
}
