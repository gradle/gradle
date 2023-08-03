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
import org.gradle.api.artifacts.VersionCatalog;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParser;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.catalog.AbstractExternalDependencyFactory.BundleFactory;
import org.gradle.api.internal.catalog.AbstractExternalDependencyFactory.PluginFactory;
import org.gradle.api.internal.catalog.AbstractExternalDependencyFactory.VersionFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.plugin.use.PluginDependency;

import java.util.List;
import java.util.Optional;

import static org.gradle.api.internal.catalog.AliasNormalizer.normalize;

public class VersionCatalogView implements VersionCatalog {

    private final DefaultVersionCatalog config;
    private final ProviderFactory providerFactory;

    private final ExternalModuleDependencyFactory dependencyFactory;
    private final BundleFactory bundleFactory;

    public VersionCatalogView(
        DefaultVersionCatalog config,
        ProviderFactory providerFactory,
        ObjectFactory objects,
        ImmutableAttributesFactory attributesFactory,
        CapabilityNotationParser capabilityNotationParser
    ) {
        this.config = config;
        this.providerFactory = providerFactory;
        this.dependencyFactory = new DefaultExternalDependencyFactory(config, providerFactory, objects, attributesFactory, capabilityNotationParser);
        this.bundleFactory = new BundleFactory(objects, providerFactory, config, attributesFactory, capabilityNotationParser);
    }

    @Override
    public final Optional<Provider<MinimalExternalModuleDependency>> findLibrary(String alias) {
        String normalizedAlias = normalize(alias);
        if (config.getLibraryAliases().contains(normalizedAlias)) {
            return Optional.of(dependencyFactory.create(normalizedAlias));
        }
        return Optional.empty();
    }

    @Override
    public final Optional<Provider<ExternalModuleDependencyBundle>> findBundle(String alias) {
        String normalizedBundle = normalize(alias);
        if (config.getBundleAliases().contains(normalizedBundle)) {
            return Optional.of(bundleFactory.createBundle(normalizedBundle));
        }
        return Optional.empty();
    }

    @Override
    public final Optional<VersionConstraint> findVersion(String alias) {
        String normalizedName = normalize(alias);
        if (config.getVersionAliases().contains(normalizedName)) {
            return Optional.of(new VersionFactory(providerFactory, config).findVersionConstraint(normalizedName));
        }
        return Optional.empty();
    }

    @Override
    public Optional<Provider<PluginDependency>> findPlugin(String alias) {
        String normalizedAlias = normalize(alias);
        if (config.getPluginAliases().contains(normalizedAlias)) {
            return Optional.of(new PluginFactory(providerFactory, config).createPlugin(normalizedAlias));
        }
        return Optional.empty();
    }

    @Override
    public final String getName() {
        return config.getName();
    }

    @Override
    public List<String> getLibraryAliases() {
        return config.getLibraryAliases();
    }

    @Override
    public List<String> getBundleAliases() {
        return config.getBundleAliases();
    }

    @Override
    public List<String> getVersionAliases() {
        return config.getVersionAliases();
    }

    @Override
    public List<String> getPluginAliases() {
        return config.getPluginAliases();
    }
}
