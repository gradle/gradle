/*
 * Copyright 2022 the original author or authors.
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

@file:Incubating
@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER")

package org.gradle.kotlin.dsl

import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.dsl.Dependencies
import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.api.artifacts.dsl.DependencyFactory
import org.gradle.api.artifacts.dsl.DependencyModifier
import org.gradle.api.artifacts.dsl.GradleDependencies
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderConvertible


/**
 * This file is used to add [Kotlin extension functions](https://kotlinlang.org/docs/extensions.html) to [Dependencies], [DependencyCollector] and [DependencyModifier] to make the Kotlin DSL more idiomatic.
 *
 * These extension functions allow an interface to implement a dependencies block in the Kotlin DSL by
 * - exposing an instance of [DependencyCollector] to add dependencies without explicitly calling [DependencyCollector.add] or [DependencyCollector.addConstraint]
 * - exposing an instance of [DependencyModifier] to modify dependencies without explicitly calling [DependencyModifier.modify]
 *
 * There are `invoke(...)` equivalents for all the `add(...)` and `addConstraint(...)` methods in [DependencyCollector].
 *
 * There are `invoke(...)` equivalents for all the `modify(...)` methods in [DependencyModifier].
 *
 * @since 8.0
 *
 * @see org.gradle.api.internal.artifacts.dsl.dependencies.DependenciesExtensionModule
 * @see Dependencies
 * @see DependencyCollector
 * @see DependencyModifier
 * @see DependencyFactory
 *
 * @sample DependenciesExtensions.sample
 */
@Suppress("unused")
private
class DependenciesExtensions {
    interface MyDependencies : GradleDependencies {
        val implementation: DependencyCollector
        val testFixtures: DependencyModifier
        operator fun invoke(action: Action<in MyDependencies>)
    }

    fun sample(dependencies: MyDependencies) {
        // In a Kotlin DSL build script
        dependencies {
            // Add a dependency by String
            implementation("org:foo:1.0") // is getImplementation().add("org:foo:1.0")

            // Add a dependency with explicit coordinate parameters
            implementation(module(group = "org", name = "foo", version = "1.0")) // is getImplementation().add(module("org", "foo", "1.0"))

            // Add dependencies on projects
            implementation(project(":path")) // is getImplementation().add(project(":path"))
            implementation(project()) // is getImplementation().add(project())

            // Add a dependency on the Gradle API
            implementation(gradleApi()) // is getImplementation().add(gradleApi())

            // Modify a dependency to select test fixtures
            implementation(testFixtures("org:foo:1.0")) // is getImplementation().add(getTestFixtures().modify("org:foo:1.0"))

            // Add a constraint by String
            implementation(constraint("org:foo:1.0")) // is getImplementation().addConstraint(constraint("org:foo:1.0"))

            // Add a constraint on projects
            implementation(constraint(project(":path"))) // is getImplementation().addConstraint(constraint(project(":path")))
            implementation(constraint(project())) // is getImplementation().addConstraint(constraint(project()))
        }
    }
}


// The #module and #constraint methods here allow the usage of named arguments in Kotlin, even though the signature is overall the same as the Java method.


/**
 * Creates a dependency based on the group, name and version (GAV) coordinates.
 *
 * @since 8.0
 */
fun Dependencies.module(group: String?, name: String, version: String?): ExternalModuleDependency = module(group, name, version)


/**
 * Modifies a dependency to select the variant of the given module.
 *
 * @see DependencyModifier
 * @since 8.0
 */
operator fun <D : ModuleDependency> DependencyModifier.invoke(dependency: D): D = modify(dependency)


/**
 * Modifies a dependency to select the variant of the given module.
 *
 * @see DependencyModifier
 * @since 8.0
 */
operator fun DependencyModifier.invoke(dependencyNotation: CharSequence): ExternalModuleDependency = modify(dependencyNotation)


/**
 * Modifies a dependency to select the variant of the given module.
 *
 * @see DependencyModifier
 * @since 8.0
 */
@Incubating
operator fun DependencyModifier.invoke(dependency: ProviderConvertible<out MinimalExternalModuleDependency>): Provider<out MinimalExternalModuleDependency> = modify(dependency)


/**
 * Modifies a dependency to select the variant of the given module.
 *
 * @see DependencyModifier
 * @since 8.0
 */
operator fun DependencyModifier.invoke(dependency: Provider<out ModuleDependency>): Provider<out ModuleDependency> = modify(dependency)


/**
 * Add a dependency.
 *
 * @param dependencyNotation dependency to add
 * @see DependencyFactory.create
 * @since 8.6
 */
operator fun DependencyCollector.invoke(dependencyNotation: CharSequence) = add(dependencyNotation)


/**
 * Add a dependency.
 *
 * @param dependencyNotation dependency to add
 * @param configuration an action to configure the dependency
 * @see DependencyFactory.create
 * @since 8.6
 */
operator fun DependencyCollector.invoke(dependencyNotation: CharSequence, configuration: Action<in ExternalModuleDependency>) = add(dependencyNotation, configuration)


/**
 * Add a dependency.
 *
 * @param files files to add as a dependency
 * @since 8.6
 */
operator fun DependencyCollector.invoke(files: FileCollection) = add(files)


/**
 * Add a dependency.
 *
 * @param files files to add as a dependency
 * @param configuration an action to configure the dependency
 * @since 8.6
 */
operator fun DependencyCollector.invoke(files: FileCollection, configuration: Action<in FileCollectionDependency>) = add(files, configuration)


/**
 * Add a dependency.
 *
 * @param externalModule external module to add as a dependency
 * @since 8.6
 */
@Incubating
operator fun DependencyCollector.invoke(externalModule: ProviderConvertible<out MinimalExternalModuleDependency>) = add(externalModule)


/**
 * Add a dependency.
 *
 * @param externalModule external module to add as a dependency
 * @param configuration an action to configure the dependency
 * @since 8.6
 */
@Incubating
operator fun DependencyCollector.invoke(externalModule: ProviderConvertible<out MinimalExternalModuleDependency>, configuration: Action<in ExternalModuleDependency>) = add(externalModule, configuration)


/**
 * Add a dependency.
 *
 * @param dependency dependency to add
 * @since 8.6
 */
operator fun DependencyCollector.invoke(dependency: Dependency) = add(dependency)


/**
 * Add a dependency.
 *
 * @param dependency dependency to add
 * @param configuration an action to configure the dependency
 * @since 8.6
 */
operator fun <D : Dependency> DependencyCollector.invoke(dependency: D, configuration: Action<in D>) = add(dependency, configuration)


/**
 * Add a dependency.
 *
 * @param dependency dependency to add
 * @since 8.6
 */
operator fun DependencyCollector.invoke(dependency: Provider<out Dependency>) = add(dependency)


/**
 * Add a dependency.
 *
 * @param dependency dependency to add
 * @param configuration an action to configure the dependency
 * @since 8.6
 */
operator fun <D : Dependency> DependencyCollector.invoke(dependency: Provider<out D>, configuration: Action<in D>) = add(dependency, configuration)


/**
 * Add a dependency constraint.
 *
 * @param dependencyConstraint dependency constraint to add
 * @since 8.7
 */
operator fun DependencyCollector.invoke(dependencyConstraint: DependencyConstraint) = addConstraint(dependencyConstraint)


/**
 * Add a dependency constraint.
 *
 * @param dependencyConstraint dependency constraint to add
 * @param configuration an action to configure the dependency constraint
 * @since 8.7
 */
operator fun DependencyCollector.invoke(dependencyConstraint: DependencyConstraint, configuration: Action<in DependencyConstraint>) = addConstraint(dependencyConstraint, configuration)


/**
 * Add a dependency constraint.
 *
 * @param dependencyConstraint dependency constraint to add
 * @since 8.7
 */
@JvmName("invokeConstraint")
operator fun DependencyCollector.invoke(dependencyConstraint: Provider<out DependencyConstraint>) = addConstraint(dependencyConstraint)


/**
 * Add a dependency constraint.
 *
 * @param dependencyConstraint dependency constraint to add
 * @param configuration an action to configure the dependency constraint
 * @since 8.7
 */
@JvmName("invokeConstraint")
operator fun DependencyCollector.invoke(dependencyConstraint: Provider<out DependencyConstraint>, configuration: Action<in DependencyConstraint>) = addConstraint(dependencyConstraint, configuration)
