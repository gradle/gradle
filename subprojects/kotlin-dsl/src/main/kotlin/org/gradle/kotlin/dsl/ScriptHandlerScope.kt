/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.dsl.DependencyConstraintHandler
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.initialization.dsl.ScriptHandler.CLASSPATH_CONFIGURATION
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderConvertible
import org.gradle.kotlin.dsl.support.delegates.ScriptHandlerDelegate
import org.gradle.kotlin.dsl.support.unsafeLazy


/**
 * Receiver for the `buildscript` block.
 */
class ScriptHandlerScope
private constructor(
    override val delegate: ScriptHandler
) : ScriptHandlerDelegate() {

    companion object {
        fun of(scriptHandler: ScriptHandler) =
            ScriptHandlerScope(scriptHandler)
    }

    /**
     * The dependencies of the script.
     */
    val dependencies by unsafeLazy { DependencyHandlerScope.of(delegate.dependencies) }

    /**
     * The script classpath configuration.
     */
    val NamedDomainObjectContainer<Configuration>.classpath: NamedDomainObjectProvider<Configuration>
        get() = named(CLASSPATH_CONFIGURATION)

    /**
     * Adds a dependency to the script classpath.
     *
     * @param dependencyNotation notation for the dependency to be added.
     * @return The dependency.
     *
     * @see [DependencyHandler.add]
     */
    fun DependencyHandler.classpath(dependencyNotation: Any): Dependency? =
        add(CLASSPATH_CONFIGURATION, dependencyNotation)

    /**
     * Adds a dependency to the script classpath.
     *
     * @param dependencyNotation notation for the dependency to be added.
     * @param dependencyConfiguration expression to use to configure the dependency.
     * @return The dependency.
     *
     * @see [DependencyHandler.add]
     */
    inline fun DependencyHandler.classpath(
        dependencyNotation: String,
        dependencyConfiguration: ExternalModuleDependency.() -> Unit
    ): ExternalModuleDependency = add(CLASSPATH_CONFIGURATION, dependencyNotation, dependencyConfiguration)

    /**
     * Adds a dependency to the script classpath.
     *
     * @param dependencyNotation notation for the dependency to be added.
     * @param dependencyConfiguration expression to use to configure the dependency.
     * @return The dependency.
     *
     * @see [DependencyHandler.add]
     * @since 7.4
     */
    fun DependencyHandler.classpath(
        dependencyNotation: Provider<MinimalExternalModuleDependency>,
        dependencyConfiguration: ExternalModuleDependency.() -> Unit
    ) {
        addProvider(CLASSPATH_CONFIGURATION, dependencyNotation, dependencyConfiguration)
    }

    /**
     * Adds a dependency to the script classpath.
     *
     * @param dependencyNotation notation for the dependency to be added.
     * @param dependencyConfiguration expression to use to configure the dependency.
     * @return The dependency.
     *
     * @see [DependencyHandler.add]
     * @since 7.4
     */
    fun DependencyHandler.classpath(
        dependencyNotation: ProviderConvertible<MinimalExternalModuleDependency>,
        dependencyConfiguration: ExternalModuleDependency.() -> Unit
    ) {
        addProviderConvertible(CLASSPATH_CONFIGURATION, dependencyNotation, dependencyConfiguration)
    }

    /**
     * Adds a dependency to the script classpath.
     *
     * @param group the group of the module to be added as a dependency.
     * @param name the name of the module to be added as a dependency.
     * @param version the optional version of the module to be added as a dependency.
     * @param configuration the optional configuration of the module to be added as a dependency.
     * @param classifier the optional classifier of the module artifact to be added as a dependency.
     * @param ext the optional extension of the module artifact to be added as a dependency.
     * @return The dependency.
     *
     * @see [DependencyHandler.add]
     */
    fun DependencyHandler.classpath(
        group: String,
        name: String,
        version: String? = null,
        configuration: String? = null,
        classifier: String? = null,
        ext: String? = null
    ): ExternalModuleDependency = create(group, name, version, configuration, classifier, ext).also {
        add(CLASSPATH_CONFIGURATION, it)
    }

    /**
     * Adds a dependency to the script classpath.
     *
     * @param group the group of the module to be added as a dependency.
     * @param name the name of the module to be added as a dependency.
     * @param version the optional version of the module to be added as a dependency.
     * @param configuration the optional configuration of the module to be added as a dependency.
     * @param classifier the optional classifier of the module artifact to be added as a dependency.
     * @param ext the optional extension of the module artifact to be added as a dependency.
     * @param dependencyConfiguration expression to use to configure the dependency.
     * @return The dependency.
     *
     * @see [DependencyHandler.create]
     * @see [DependencyHandler.add]
     */
    inline fun DependencyHandler.classpath(
        group: String,
        name: String,
        version: String? = null,
        configuration: String? = null,
        classifier: String? = null,
        ext: String? = null,
        dependencyConfiguration: ExternalModuleDependency.() -> Unit
    ): ExternalModuleDependency = create(group, name, version, configuration, classifier, ext).also {
        add(CLASSPATH_CONFIGURATION, it, dependencyConfiguration)
    }

    /**
     * Adds a dependency to the script classpath.
     *
     * @param dependency dependency to be added.
     * @param dependencyConfiguration expression to use to configure the dependency.
     * @return The dependency.
     *
     * @see [DependencyHandler.add]
     */
    inline fun <T : ModuleDependency> DependencyHandler.classpath(
        dependency: T,
        dependencyConfiguration: T.() -> Unit
    ): T = add(CLASSPATH_CONFIGURATION, dependency, dependencyConfiguration)

    /**
     * Adds a dependency constraint to the script classpath configuration.
     *
     * @param dependencyConstraintNotation the dependency constraint notation
     *
     * @return the added dependency constraint
     *
     * @see [DependencyConstraintHandler.add]
     * @since 5.0
     */
    fun DependencyConstraintHandler.classpath(dependencyConstraintNotation: Any): DependencyConstraint? =
        add(CLASSPATH_CONFIGURATION, dependencyConstraintNotation)

    /**
     * Adds a dependency constraint to the script classpath configuration.
     *
     * @param dependencyConstraintNotation the dependency constraint notation
     * @param configuration the block to use to configure the dependency constraint
     *
     * @return the added dependency constraint
     *
     * @see [DependencyConstraintHandler.add]
     * @since 5.0
     */
    fun DependencyConstraintHandler.classpath(dependencyConstraintNotation: Any, configuration: DependencyConstraint.() -> Unit): DependencyConstraint? =
        add(CLASSPATH_CONFIGURATION, dependencyConstraintNotation, configuration)
}
