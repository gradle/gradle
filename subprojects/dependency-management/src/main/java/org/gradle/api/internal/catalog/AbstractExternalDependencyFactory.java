/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.api.internal.catalog;

import org.gradle.api.artifacts.ExternalModuleDependencyBundle;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.plugin.use.PluginDependency;

import javax.inject.Inject;

public abstract class AbstractExternalDependencyFactory implements ExternalModuleDependencyFactory {
    protected final DefaultVersionCatalog config;
    protected final ProviderFactory providers;

    @SuppressWarnings("unused")
    public static abstract class SubDependencyFactory implements ExternalModuleDependencyFactory {
        protected final AbstractExternalDependencyFactory owner;

        protected SubDependencyFactory(AbstractExternalDependencyFactory owner) {
            this.owner = owner;
        }

        @Override
        public Provider<MinimalExternalModuleDependency> create(String alias) {
            return owner.create(alias);
        }

    }

    @Inject
    protected AbstractExternalDependencyFactory(DefaultVersionCatalog config,
                                                ProviderFactory providers) {
        this.config = config;
        this.providers = providers;
    }

    @Override
    public Provider<MinimalExternalModuleDependency> create(String alias) {
        return providers.of(DependencyValueSource.class,
            spec -> spec.getParameters().getDependencyData().set(config.getDependencyData(alias)))
            .forUseAtConfigurationTime();
    }

    public static class VersionFactory {
        protected final ProviderFactory providers;
        protected final DefaultVersionCatalog config;

        public VersionFactory(ProviderFactory providers, DefaultVersionCatalog config) {
            this.providers = providers;
            this.config = config;
        }

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
            ImmutableVersionConstraint version = findVersionConstraint(name);
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

        protected ImmutableVersionConstraint findVersionConstraint(String name) {
            return config.getVersion(name).getVersion();
        }
    }

    public static class BundleFactory {
        protected final ProviderFactory providers;
        protected final DefaultVersionCatalog config;

        public BundleFactory(ProviderFactory providers, DefaultVersionCatalog config) {
            this.providers = providers;
            this.config = config;
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

    public static class PluginFactory {
        protected final ProviderFactory providers;
        protected final DefaultVersionCatalog config;

        public PluginFactory(ProviderFactory providers, DefaultVersionCatalog config) {
            this.providers = providers;
            this.config = config;
        }

        protected Provider<PluginDependency> createPlugin(String name) {
            return providers.of(PluginDependencyValueSource.class,
                spec -> spec.parameters(params -> {
                    params.getConfig().set(config);
                    params.getPluginName().set(name);
                }))
                .forUseAtConfigurationTime();
        }
    }
}
