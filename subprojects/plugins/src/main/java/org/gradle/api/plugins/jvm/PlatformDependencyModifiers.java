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

package org.gradle.api.plugins.jvm;

import org.gradle.api.Incubating;
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;

import javax.annotation.Nullable;

/**
 * Dependency APIs for using <a href="https://docs.gradle.org/current/userguide/java_platform_plugin.html#java_platform_plugin">Platforms</a> in {@code dependencies} blocks.
 *
 * @since 7.6
 */
@Incubating
public interface PlatformDependencyModifiers extends Dependencies {

    /**
     * Creates an {@link ExternalModuleDependency} for the given dependency notation and modifies it to select the Platform variant of the given module.
     *
     * @param dependencyNotation dependency notation
     * @return the modified dependency
     * @see DependencyFactory#create(CharSequence) Valid dependency notation for this method
     */
    default ExternalModuleDependency platform(CharSequence dependencyNotation) {
        return platform(getDependencyFactory().create(dependencyNotation));
    }

    /**
     * Creates an {@link ExternalModuleDependency} for the given group, name and version and modifies it to select the Platform variant of the given module.
     *
     * @param group the group
     * @param name the name
     * @param version the version
     * @return the modified dependency
     * @see DependencyFactory#create(String, String, String)
     */
    default ExternalModuleDependency platform(@Nullable String group, String name, @Nullable String version) {
        return platform(getDependencyFactory().create(group, name, version));
    }

    /**
     * Takes a given {@link ModuleDependency} and modifies it to select the Platform variant of the given module.
     *
     * @param dependency the dependency
     * @return the modified dependency
     */
    <D extends ModuleDependency> D platform(D dependency);

    /**
     * Takes a given {@code Provider} to a {@link MinimalExternalModuleDependency} and modifies the dependency to select the Platform variant of the given module.
     *
     * @param providerConvertibleToDependency the provider
     * @return a provider to the modified dependency
     */
    default Provider<? extends MinimalExternalModuleDependency> platform(ProviderConvertible<? extends MinimalExternalModuleDependency> providerConvertibleToDependency) {
        return providerConvertibleToDependency.asProvider().map(this::platform);
    }

    /**
     * Takes a given {@code Provider} to a {@link ExternalModuleDependency} and modifies the dependency to select the Platform variant of the given module.
     *
     * @param providerToDependency the provider
     * @return a provider to the modified dependency
     */
    default Provider<? extends ExternalModuleDependency> platform(Provider<? extends ExternalModuleDependency> providerToDependency) {
        return providerToDependency.map(this::platform);
    }


    /**
     * Creates an {@link ExternalModuleDependency} for the given dependency notation and modifies it to select the Enforced Platform variant of the given module.
     *
     * @param dependencyNotation dependency notation
     * @return the modified dependency
     * @see DependencyFactory#create(CharSequence) Valid dependency notation for this method
     */
    default ExternalModuleDependency enforcedPlatform(CharSequence dependencyNotation) {
        return enforcedPlatform(getDependencyFactory().create(dependencyNotation));
    }

    /**
     * Creates an {@link ExternalModuleDependency} for the given group, name and version and modifies it to select the Enforced Platform variant of the given module.
     *
     * @param group the group
     * @param name the name
     * @param version the version
     * @return the modified dependency
     * @see DependencyFactory#create(String, String, String)
     */
    default ExternalModuleDependency enforcedPlatform(@Nullable String group, String name, @Nullable String version) {
        return enforcedPlatform(getDependencyFactory().create(group, name, version));
    }

    /**
     * Takes a given {@link ModuleDependency} and modifies it to select the Enforced Platform variant of the given module.
     *
     * @param dependency the dependency
     * @return the modified dependency
     */
    <D extends ModuleDependency> D enforcedPlatform(D dependency);

    /**
     * Takes a given {@link ExternalDependency} and modifies it to select the Enforced Platform variant of the given module.
     *
     * @param dependency the dependency
     * @return the modified dependency
     */
    <D extends ExternalDependency> D enforcedPlatform(D dependency);

    /**
     * Takes a given {@code Provider} to a {@link MinimalExternalModuleDependency} and modifies the dependency to select the Enforced Platform variant of the given module.
     *
     * @param providerConvertibleToDependency the provider
     * @return a provider to the modified dependency
     */
    default Provider<? extends MinimalExternalModuleDependency> enforcedPlatform(ProviderConvertible<? extends MinimalExternalModuleDependency> providerConvertibleToDependency) {
        return providerConvertibleToDependency.asProvider().map(this::enforcedPlatform);
    }

    /**
     * Takes a given {@code Provider} to a {@link ExternalModuleDependency} and modifies the dependency to select the Enforced Platform variant of the given module.
     *
     * @param providerToDependency the provider
     * @return a provider to the modified dependency
     */
    default Provider<? extends ExternalModuleDependency> enforcedPlatform(Provider<? extends ExternalModuleDependency> providerToDependency) {
        return providerToDependency.map(this::enforcedPlatform);
    }
}
