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

import org.gradle.api.Incubating
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.kotlin.dsl.accessors.runtime.externalModuleDependencyFor
import org.gradle.kotlin.dsl.support.excludeMapFor
import org.gradle.kotlin.dsl.support.uncheckedCast


/**
 * Creates a dependency on a module without adding it to a configuration.
 *
 * @param group the group of the module to be added as a dependency.
 * @param name the name of the module to be added as a dependency.
 * @param version the optional version of the module to be added as a dependency.
 * @param configuration the optional configuration of the module to be added as a dependency.
 * @param classifier the optional classifier of the module artifact to be added as a dependency.
 * @param ext the optional extension of the module artifact to be added as a dependency.
 *
 * @return The dependency.
 *
 * @see [DependencyHandler.create]
 */
fun DependencyHandler.create(
    group: String,
    name: String,
    version: String? = null,
    configuration: String? = null,
    classifier: String? = null,
    ext: String? = null
): ExternalModuleDependency = externalModuleDependencyFor(
    this,
    group,
    name,
    version,
    configuration,
    classifier,
    ext
)


/**
 * Creates a dependency without adding it to a configuration.
 *
 * @param dependencyNotation The dependency donation.
 * @param dependencyConfiguration The expression to use to configure the dependency.
 *
 * @return The dependency.
 *
 * @since 7.6
 */
@Incubating
inline fun DependencyHandler.create(dependencyNotation: String, dependencyConfiguration: ExternalModuleDependency.() -> Unit): ExternalModuleDependency =

    (create(dependencyNotation) as ExternalModuleDependency).apply(dependencyConfiguration)


/**
 * Creates a dependency on a project without adding it to a configuration.
 *
 * @param path the path of the project to be added as a dependency.
 * @param configuration the optional configuration of the project to be added as a dependency.
 * @return The dependency.
 */
fun DependencyHandler.project(
    path: String,
    configuration: String? = null
): ProjectDependency =

    uncheckedCast(
        project(
            if (configuration != null) mapOf("path" to path, "configuration" to configuration)
            else mapOf("path" to path)
        )
    )


/**
 * Adds a dependency to the given configuration, and configures the dependency using the given expression.
 *
 * @param configuration The name of the configuration.
 * @param dependencyNotation The dependency notation.
 * @param dependencyConfiguration The expression to use to configure the dependency.
 * @return The dependency.
 */
inline fun DependencyHandler.add(
    configuration: String,
    dependencyNotation: String,
    dependencyConfiguration: ExternalModuleDependency.() -> Unit
): ExternalModuleDependency =

    add(configuration, create(dependencyNotation) as ExternalModuleDependency, dependencyConfiguration)


/**
 * Adds a dependency to the given configuration, and configures the dependency using the given expression.
 *
 * @param configuration The name of the configuration.
 * @param dependency The dependency.
 * @param dependencyConfiguration The expression to use to configure the dependency.
 * @return The dependency.
 */
inline fun <T : ModuleDependency> DependencyHandler.add(
    configuration: String,
    dependency: T,
    dependencyConfiguration: T.() -> Unit
): T =

    dependency.apply {
        dependencyConfiguration()
        add(configuration, this)
    }


/**
 * Adds an exclude rule to exclude transitive dependencies of this dependency.
 *
 * Excluding a particular transitive dependency does not guarantee that it does not show up
 * in the dependencies of a given configuration.
 * For example, some other dependency, which does not have any exclude rules,
 * might pull in exactly the same transitive dependency.
 * To guarantee that the transitive dependency is excluded from the entire configuration
 * please use per-configuration exclude rules: [Configuration.getExcludeRules].
 * In fact, in majority of cases the actual intention of configuring per-dependency exclusions
 * is really excluding a dependency from the entire configuration (or classpath).
 *
 * If your intention is to exclude a particular transitive dependency
 * because you don't like the version it pulls in to the configuration
 * then consider using the forced versions feature: [ResolutionStrategy.force].
 *
 * @param group the optional group identifying the dependencies to be excluded.
 * @param module the optional module name identifying the dependencies to be excluded.
 * @return this
 *
 * @see [ModuleDependency.exclude]
 */
fun <T : ModuleDependency> T.exclude(group: String? = null, module: String? = null): T =
    uncheckedCast(exclude(excludeMapFor(group, module)))
