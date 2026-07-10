/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations;

import org.gradle.api.DomainObjectCollection;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * This class manages a set of ExtendedConfiguration instances, which can be backed by either realized configurations or provided configurations.
 * It handles correctly calling the validation action whenever a configuration is realized.  In other words, it ensures that the validation action
 * is always called whenever the configuration is realized and prevents access to the underlying provider which could potentially allow realizing
 * the configuration without validation.
 */
class ExtendedConfigurations {
    private final Consumer<Configuration> validationAction;
    private final ProviderFactory providerFactory;
    private final Set<ExtendedConfiguration> configurations = new LinkedHashSet<>();

    public ExtendedConfigurations(Consumer<Configuration> validationAction, ProviderFactory providerFactory) {
        this.validationAction = validationAction;
        this.providerFactory = providerFactory;
    }

    /**
     * Adds a realized configuration.
     */
    public void add(Configuration configuration) {
        // For realized configurations, we can call the validation action immediately.
        validationAction.accept(configuration);
        configurations.add(new RealizedExtendedConfiguration(configuration, providerFactory));
    }

    /**
     * Adds a provided configuration.  The validation action associated with this object will be called when the configuration is realized.
     */
    public void add(Provider<? extends Configuration> configurationProvider) {
        configurations.add(new ProvidedExtendedConfiguration(configurationProvider, validationAction, providerFactory));
    }

    /**
     * Returns true if there are no extended configurations.
     */
    public boolean isEmpty() {
        return configurations.isEmpty();
    }

    /**
     * Visits all extended configurations.  Visiting an extended configuration does not realize it.
     */
    public void visitConfigurations(ExtendedConfiguration.Visitor visitor) {
        configurations.forEach(visitor::visit);
    }

    private static class ProvidedExtendedConfiguration implements ExtendedConfiguration {
        private final Provider<? extends Configuration> provider;
        private final Consumer<Configuration> validationAction;
        private final ProviderFactory providerFactory;

        ProvidedExtendedConfiguration(Provider<? extends Configuration> provider, Consumer<Configuration> validationAction, ProviderFactory providerFactory) {
            this.provider = provider;
            this.validationAction = validationAction;
            this.providerFactory = providerFactory;
        }

        @Override
        public Configuration get() {
            // It's important that we call get() first, and then call the validation action, to ensure that the extended configuration graph is realized before validating.
            // We can't wrap validation in a provider since the validation action could realize other configurations, causing reentrant calls to get() in the
            // case where there is a cycle between configurations.  In other words, this order allows us to realize all extended configurations first, and then
            // discover and report cycles during the validation action.
            Configuration configuration = provider.get();
            validationAction.accept(configuration);
            return configuration;
        }

        @Override
        public <T> Provider<DomainObjectCollection<? extends T>> mapToCollection(Function<Configuration, DomainObjectCollection<T>> configurationToCollection) {
            return providerFactory.provider(() -> configurationToCollection.apply(get()));
        }
    }

    private static class RealizedExtendedConfiguration implements ExtendedConfiguration {
        private final Configuration configuration;
        private final ProviderFactory providerFactory;

        RealizedExtendedConfiguration(Configuration configuration, ProviderFactory providerFactory) {
            this.configuration = configuration;
            this.providerFactory = providerFactory;
        }

        @Override
        public Configuration get() {
            return configuration;
        }

        @Override
        public <T> Provider<DomainObjectCollection<? extends T>> mapToCollection(Function<Configuration, DomainObjectCollection<T>> configurationToCollection) {
            return providerFactory.provider(() -> configurationToCollection.apply(configuration));
        }
    }
}
