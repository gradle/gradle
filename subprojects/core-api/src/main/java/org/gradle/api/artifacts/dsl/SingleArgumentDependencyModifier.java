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

import org.gradle.api.Incubating;
import org.gradle.api.NonExtensible;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;
import org.gradle.internal.Cast;

import javax.inject.Inject;

/**
 * A {@code SingleArgumentDependencyModifier} defines how to modify a dependency inside a custom {@code dependencies}
 * block to select a different variant, while accepting a single parameter controlling how the dependency is modified.
 *
 * @apiNote
 * Gradle has specific extensions to make explicit calls to {@code modify(...)} unnecessary from the DSL.
 * <ul>
 * <li>For Groovy DSL, we create {@code call(...)} equivalents for all the {@code modify(...)} methods.</li>
 * <li>For Kotlin DSL, we create {@code invoke(...)} equivalents for all the {@code modify(...)} methods.</li>
 * </ul>
 * @implSpec The only method that should be implemented is {@link #modifyImplementation(ModuleDependency, T)}. Other {@code abstract} methods are used to inject necessary services
 * and should not be implemented.
 * @implNote All implementations of {@code modify(...)} delegate to {@link #modifyImplementation(ModuleDependency, T)}.
 * <p>
 * Changes to this interface may require changes to the
 * {@link org.gradle.api.internal.artifacts.dsl.dependencies.DependenciesExtensionModule extension module for Groovy DSL} or
 * {@link org.gradle.kotlin.dsl.DependenciesExtensions extension functions for Kotlin DSL}.
 *
 * @since 8.13
 *
 * @see DependencyModifier
 */
@Incubating
@NonExtensible
@SuppressWarnings("JavadocReference")
public abstract class SingleArgumentDependencyModifier<T> {

    /**
     * Creates a new instance.
     *
     * @since 8.13
     */
    protected SingleArgumentDependencyModifier() {
    }

    /**
     * A dependency factory is used to convert supported dependency notations into {@link org.gradle.api.artifacts.Dependency} instances.
     *
     * @return a dependency factory
     * @implSpec Do not implement this method. Gradle generates the implementation automatically.
     *
     * @see DependencyFactory
     */
    @Inject
    protected abstract DependencyFactory getDependencyFactory();

    /**
     * Create an {@link ExternalModuleDependency} from the given notation and modifies it to select the variant of the given module as described in {@link #modify(ModuleDependency, Object)}.
     *
     * @param dependencyNotation the dependency notation
     * @param argument the argument to control how the dependency is modified
     *
     * @return the modified dependency
     *
     * @since 8.13
     *
     * @see DependencyFactory#create(CharSequence)
     */
    public final ExternalModuleDependency modify(CharSequence dependencyNotation, T argument) {
        return modify(getDependencyFactory().create(dependencyNotation), argument);
    }

    /**
     * Takes a given {@code Provider} to a {@link MinimalExternalModuleDependency} and modifies the dependency to select the variant of the given module as described in {@link #modify(ModuleDependency, Object)}.
     *
     * @param providerConvertibleToDependency the provider
     * @param argument the argument to control how the dependency is modified
     *
     * @since 8.13
     *
     * @return a provider to the modified dependency
     */
    public final Provider<? extends MinimalExternalModuleDependency> modify(ProviderConvertible<? extends MinimalExternalModuleDependency> providerConvertibleToDependency, T argument) {
        return providerConvertibleToDependency.asProvider().map(d -> modify(d, argument));
    }

    /**
     * Takes a given {@code Provider} to a {@link ExternalModuleDependency} and modifies the dependency to select the variant of the given module as described in {@link #modify(ModuleDependency, Object)}.
     *
     * @param providerToDependency the provider
     * @param argument the argument to control how the dependency is modified
     *
     * @since 8.13
     *
     * @return a provider to the modified dependency
     */
    public final <D extends ModuleDependency> Provider<D> modify(Provider<D> providerToDependency, T argument) {
        return providerToDependency.map(d -> modify(d, argument));
    }

    /**
     * Takes a given {@link ModuleDependency} and modifies it.
     *
     * <p>
     * The dependency will be copied, so the original dependency will not be modified.
     * </p>
     *
     * @param dependency the dependency to modify
     * @param <D> the type of the {@link ModuleDependency}
     *
     * @since 8.13
     *
     * @return the modified dependency
     */
    public final <D extends ModuleDependency> D modify(D dependency, T argument) {
        // Enforce a copy of the dependency to avoid modifying the original dependency
        // The unchecked cast can be incorrect if D is an implementation type, but it shouldn't be used in that way.
        D copy = Cast.uncheckedNonnullCast(dependency.copy());
        modifyImplementation(copy, argument);
        return copy;
    }

    /**
     * Modify the given dependency.
     *
     * @param dependency the dependency to modify
     * @param argument the argument to control how the dependency is modified
     *
     * @since 8.13
     *
     * @implSpec This method must be implemented.
     */
    protected abstract void modifyImplementation(ModuleDependency dependency, T argument);

}
