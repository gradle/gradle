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
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;

/**
 * Dependency APIs for using <a href="https://docs.gradle.org/current/userguide/java_platform_plugin.html#java_platform_plugin">Platforms</a> in {@code dependencies} blocks.
 *
 * <p>
 * NOTE: This API is <strong>incubating</strong> and is likely to change until it's made stable.
 * </p>
 *
 * @since 7.6
 */
@Incubating
public interface PlatformDependencyModifiers extends Dependencies {
    /**
     * Create an {@link ExternalModuleDependency} from the given notation and modifies it to select the Platform variant of the given module.
     *
     * @param dependencyNotation the dependency notation
     * @return the modified dependency
     * @see DependencyFactory#create(CharSequence)
     */
    default ExternalModuleDependency platform(CharSequence dependencyNotation) {
        return platform(getDependencyFactory().create(dependencyNotation));
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
     * Takes a given {@code Provider} to a {@link ModuleDependency} and modifies the dependency to select the Platform variant of the given module.
     *
     * @param providerToDependency the provider
     * @return a provider to the modified dependency
     */
    default <D extends ModuleDependency> Provider<D> platform(Provider<D> providerToDependency) {
        return providerToDependency.map(this::platform);
    }

    /**
     * Create an {@link ExternalModuleDependency} from the given notation and modifies it to select the Enforced Platform variant of the given module.
     *
     * @param dependencyNotation the dependency notation
     * @return the modified dependency
     * @see DependencyFactory#create(CharSequence)
     */
    default ExternalModuleDependency enforcedPlatform(CharSequence dependencyNotation) {
        return enforcedPlatform(getDependencyFactory().create(dependencyNotation));
    }

    /**
     * Takes a given {@link ModuleDependency} and modifies it to select the Enforced Platform variant of the given module.
     *
     * @param dependency the dependency
     * @return the modified dependency
     */
    <D extends ModuleDependency> D enforcedPlatform(D dependency);

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
     * Takes a given {@code Provider} to a {@link ModuleDependency} and modifies the dependency to select the Enforced Platform variant of the given module.
     *
     * @param providerToDependency the provider
     * @return a provider to the modified dependency
     */
    default <D extends ModuleDependency> Provider<D> enforcedPlatform(Provider<D> providerToDependency) {
        return providerToDependency.map(this::enforcedPlatform);
    }
}
