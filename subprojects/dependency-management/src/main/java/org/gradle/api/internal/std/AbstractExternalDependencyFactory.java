/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.std;

import org.gradle.api.artifacts.ExternalModuleDependencyBundle;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

import javax.inject.Inject;

public abstract class AbstractExternalDependencyFactory implements ExternalModuleDependencyFactory {
    private final AllDependenciesModel config;
    private final ProviderFactory providers;

    @Inject
    protected AbstractExternalDependencyFactory(AllDependenciesModel config,
                                                ProviderFactory providers) {
        this.config = config;
        this.providers = providers;
    }

    protected Provider<MutableVersionConstraint> createVersion(String alias) {
        return providers.of(DependencyDataVersionConstraintValueSource.class,
            spec -> spec.getParameters().getVersion().set(config.getDependencyData(alias).getVersion()))
            .forUseAtConfigurationTime();
    }

    protected Provider<MinimalExternalModuleDependency> create(String alias) {
        return providers.of(DependencyValueSource.class,
            spec -> spec.getParameters().getDependencyData().set(config.getDependencyData(alias)))
            .forUseAtConfigurationTime();
    }

    protected Provider<ExternalModuleDependencyBundle> createBundle(String name) {
        return providers.of(DependencyBundleValueSource.class,
            spec -> spec.parameters(params -> {
                params.getConfig().set(config);
                params.getBundleName().set(name);
            }))
            .forUseAtConfigurationTime();
    }
}
