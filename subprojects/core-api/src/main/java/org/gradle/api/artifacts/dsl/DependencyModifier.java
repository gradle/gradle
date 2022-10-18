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

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;

import javax.inject.Inject;

public interface DependencyModifier {
    @Inject
    DependencyFactory getDependencyFactory();

    /**
     * Create an {@link ExternalModuleDependency} from the given notation and modifies it to select the Test Fixtures variant of the given module.
     *
     * @param dependencyNotation the dependency notation
     * @return the modified dependency
     * @see DependencyFactory#create(CharSequence)
     */
    default ExternalModuleDependency modify(CharSequence dependencyNotation) {
        return modify(getDependencyFactory().create(dependencyNotation));
    }

    /**
     * Takes a given {@code Provider} to a {@link MinimalExternalModuleDependency} and modifies the dependency to select the Test Fixtures variant of the given module.
     *
     * @param providerConvertibleToDependency the provider
     * @return a provider to the modified dependency
     */
    default Provider<? extends MinimalExternalModuleDependency> modify(ProviderConvertible<? extends MinimalExternalModuleDependency> providerConvertibleToDependency) {
        return providerConvertibleToDependency.asProvider().map(this::modify);
    }

    /**
     * Takes a given {@code Provider} to a {@link ExternalModuleDependency} and modifies the dependency to select the Test Fixtures variant of the given module.
     *
     * @param providerConvertibleToDependency the provider
     * @return a provider to the modified dependency
     */
    default <D extends Dependency> Provider<D> modify(Provider<D> providerConvertibleToDependency) {
        return providerConvertibleToDependency.map(this::modify);
    }

    <D extends Dependency> D modify(D dependency);
}
