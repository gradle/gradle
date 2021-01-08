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
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

import javax.inject.Inject;
import java.util.Optional;

public abstract class AbstractExternalDependencyFactory implements ExternalModuleDependencyFactory {
    private final DefaultVersionCatalog config;
    private final ProviderFactory providers;

    protected abstract class SubDependencyFactory implements ExternalModuleDependencyFactory {
        protected Provider<MinimalExternalModuleDependency> create(String alias) {
            return AbstractExternalDependencyFactory.this.create(alias);
        }

        @Override
        public Optional<Provider<MinimalExternalModuleDependency>> findDependency(String alias) {
            return AbstractExternalDependencyFactory.this.findDependency(alias);
        }

        @Override
        public Optional<Provider<ExternalModuleDependencyBundle>> findBundle(String bundle) {
            return AbstractExternalDependencyFactory.this.findBundle(bundle);
        }

        @Override
        public Optional<VersionConstraint> findVersion(String name) {
            return AbstractExternalDependencyFactory.this.findVersion(name);
        }

        @Override
        public String getName() {
            return AbstractExternalDependencyFactory.this.getName();
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

    @Override
    public final Optional<Provider<MinimalExternalModuleDependency>> findDependency(String alias) {
        if (config.getDependencyAliases().contains(alias)) {
            return Optional.of(create(alias));
        }
        return Optional.empty();
    }

    @Override
    public final Optional<Provider<ExternalModuleDependencyBundle>> findBundle(String bundle) {
        if (config.getBundleAliases().contains(bundle)) {
            return Optional.of(new BundleFactory().createBundle(bundle));
        }
        return Optional.empty();
    }

    @Override
    public final Optional<VersionConstraint> findVersion(String name) {
        if (config.getVersionAliases().contains(name)) {
            return Optional.of(new VersionFactory().findVersionConstraint(name));
        }
        return Optional.empty();
    }

    @Override
    public final String getName() {
        return config.getName();
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

        private ImmutableVersionConstraint findVersionConstraint(String name) {
            return config.getVersion(name).getVersion();
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
