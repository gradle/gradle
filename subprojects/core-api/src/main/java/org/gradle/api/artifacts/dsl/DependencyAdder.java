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

package org.gradle.api.artifacts.dsl;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.NonExtensible;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;

/**
 * A {@code DependencyAdder} is used to add dependencies to a specific configuration.
 *
 * @apiNote
 * Gradle has specific extensions to make explicit calls to {@code add(...)} unnecessary from the DSL.
 * <ul>
 * <li>For Groovy DSL, we create {@code call(...)} equivalents for all the {@code add(...)} methods.</li>
 * <li>For Kotlin DSL, we create {@code invoke(...)} equivalents for all the {@code add(...)} methods.</li>
 * </ul>
 *
 * @implSpec This interface should not be implemented by end users or plugins.
 * @implNote
 * Changes to this interface may require changes to the
 * {@link org.gradle.api.internal.artifacts.dsl.dependencies.DependenciesExtensionModule extension module for Groovy DSL} or
 * {@link org.gradle.kotlin.dsl.DependenciesExtensions extension functions for Kotlin DSL}.
 *
 * @since 7.6
 */
@Incubating
@NonExtensible
@SuppressWarnings("JavadocReference")
public interface DependencyAdder {
    /**
     * Add a dependency.
     *
     * @param dependencyNotation dependency to add
     * @see DependencyFactory#create(CharSequence) Valid dependency notation for this method
     */
    void add(CharSequence dependencyNotation);

    /**
     * Add a dependency and configure it.
     *
     * @param dependencyNotation dependency to add
     * @param configuration an action to configure the dependency
     * @see DependencyFactory#create(CharSequence) Valid dependency notation for this method
     */
    void add(CharSequence dependencyNotation, Action<? super ExternalModuleDependency> configuration);

    /**
     * Add a dependency.
     *
     * @param files files to add as a dependency
     * @see DependencyFactory#create(FileCollection)
     */
    void add(FileCollection files);

    /**
     * Add a dependency and configure it.
     *
     * @param files files to add as a dependency
     * @param configuration an action to configure the dependency
     * @see DependencyFactory#create(FileCollection)
     */
    void add(FileCollection files, Action<? super FileCollectionDependency> configuration);

    /**
     * Add a dependency.
     *
     * @param externalModule external module to add as a dependency
     */
    void add(ProviderConvertible<? extends MinimalExternalModuleDependency> externalModule);

    /**
     * Add a dependency and configure it.
     *
     * @param externalModule external module to add as a dependency
     * @param configuration an action to configure the dependency
     */
    void add(ProviderConvertible<? extends MinimalExternalModuleDependency> externalModule, Action<? super ExternalModuleDependency> configuration);

    /**
     * Add a dependency.
     *
     * @param dependency dependency to add
     */
    void add(Dependency dependency);

    /**
     * Add a dependency and configure it.
     *
     * @param dependency dependency to add
     * @param configuration an action to configure the dependency
     */
    <D extends Dependency> void add(D dependency, Action<? super D> configuration);

    /**
     * Add a dependency.
     *
     * @param dependency dependency to add
     */
    void add(Provider<? extends Dependency> dependency);

    /**
     * Add a dependency and configure it.
     *
     * @param dependency dependency to add
     * @param configuration an action to configure the dependency
     */
    <D extends Dependency> void add(Provider<? extends D> dependency, Action<? super D> configuration);

    /**
     * Add a bundle.
     *
     * @param bundle the bundle to add
     */
    <D extends Dependency> void bundle(Iterable<? extends D> bundle);

    /**
     * Add a bundle and configure them.
     *
     * @param bundle the bundle to add
     * @param configuration an action to configure each dependency in the bundle
     */
    <D extends Dependency> void bundle(Iterable<? extends D> bundle, Action<? super D> configuration);

    /**
     * Add a bundle.
     *
     * @param bundle the bundle to add
     */
    <D extends Dependency> void bundle(Provider<? extends Iterable<? extends D>> bundle);

    /**
     * Add a bundle and configure them.
     *
     * @param bundle the bundle to add
     * @param configuration an action to configure each dependency in the bundle
     */
    <D extends Dependency> void bundle(Provider<? extends Iterable<? extends D>> bundle, Action<? super D> configuration);

    /**
     * Add a bundle.
     *
     * @param bundle the bundle to add
     */
    <D extends Dependency> void bundle(ProviderConvertible<? extends Iterable<? extends D>> bundle);

    /**
     * Add a bundle and configure them.
     *
     * @param bundle the bundle to add
     * @param configuration an action to configure each dependency in the bundle
     */
    <D extends Dependency> void bundle(ProviderConvertible<? extends Iterable<? extends D>> bundle, Action<? super D> configuration);

}
