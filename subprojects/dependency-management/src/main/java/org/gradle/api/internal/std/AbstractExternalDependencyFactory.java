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
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

import javax.inject.Inject;

public abstract class AbstractExternalDependencyFactory implements ExternalModuleDependencyFactory {
    private final DefaultVersionCatalog config;
    private final ProviderFactory providers;

    protected abstract class SubDependencyFactory implements ExternalModuleDependencyFactory {
        protected Provider<MinimalExternalModuleDependency> create(String alias) {
            return AbstractExternalDependencyFactory.this.create(alias);
        }
    }

    @Inject
    protected AbstractExternalDependencyFactory(DefaultVersionCatalog config,
                                                ProviderFactory providers) {
        this.config = config;
        this.providers = providers;
    }

    protected Provider<MinimalExternalModuleDependency> create(String alias) {
        return providers.of(DependencyValueSource.class,
            spec -> spec.getParameters().getDependencyData().set(config.getDependencyData(alias)))
            .forUseAtConfigurationTime();
    }

    protected class VersionFactory {
        /**
         * Returns a single version string from a rich version
         * constraint, assuming the user knows what they are doing.
         *
         * @param name the name of the version alias
         * @return a single version string or an empty string
         */
        protected Provider<String> getVersion(String name) {
            return providers.provider(() -> doGetVersion(name));
        }

        private String doGetVersion(String name) {
            ImmutableVersionConstraint version = config.getVersion(name).getVersion();
            String requiredVersion = version.getRequiredVersion();
            if (!requiredVersion.isEmpty()) {
                return requiredVersion;
            }
            String strictVersion = version.getStrictVersion();
            if (!strictVersion.isEmpty()) {
                return strictVersion;
            }
            return version.getPreferredVersion();
        }
    }

    protected class BundleFactory {
        protected Provider<ExternalModuleDependencyBundle> createBundle(String name) {
            return providers.of(DependencyBundleValueSource.class,
                spec -> spec.parameters(params -> {
                    params.getConfig().set(config);
                    params.getBundleName().set(name);
                }))
                .forUseAtConfigurationTime();
        }
    }
}
