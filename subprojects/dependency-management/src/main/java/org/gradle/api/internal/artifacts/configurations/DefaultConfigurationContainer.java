/*
 * Copyright 2010 the original author or authors.
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

import java.util.Collection;
import java.util.Set;

import org.gradle.api.DomainObjectSet;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.UnknownConfigurationException;
import org.gradle.api.internal.AbstractNamedDomainObjectContainer;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.ConfigurationResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultResolutionStrategy;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.listener.ListenerManager;

public class DefaultConfigurationContainer extends AbstractNamedDomainObjectContainer<Configuration> implements ConfigurationContainerInternal,
        ConfigurationsProvider {

    public static final String DETACHED_CONFIGURATION_DEFAULT_NAME = "detachedConfiguration";

    private final ConfigurationResolver resolver;

    private final Instantiator instantiator;

    private final DomainObjectContext context;

    private final ListenerManager listenerManager;

    private final DependencyMetaDataProvider dependencyMetaDataProvider;

    private int detachedConfigurationDefaultNameCounter = 1;

    public DefaultConfigurationContainer(final ConfigurationResolver resolver, final Instantiator instantiator,
                                         final DomainObjectContext context, final ListenerManager listenerManager, final DependencyMetaDataProvider dependencyMetaDataProvider) {
        super(Configuration.class, instantiator, new Configuration.Namer());
        this.resolver = resolver;
        this.instantiator = instantiator;
        this.context = context;
        this.listenerManager = listenerManager;
        this.dependencyMetaDataProvider = dependencyMetaDataProvider;
    }

    @Override
    protected Configuration doCreate(final String name) {
        return this.instantiator.newInstance(DefaultConfiguration.class, this.context.absoluteProjectPath(name), name, this, this.resolver,
                this.listenerManager, this.dependencyMetaDataProvider, this.instantiator.newInstance(DefaultResolutionStrategy.class));
    }

    @Override
    public Set<Configuration> getAll() {
        return this;
    }

    @Override
    public ConfigurationInternal getByName(final String name) {
        return (ConfigurationInternal) super.getByName(name);
    }

    @Override
    public String getTypeDisplayName() {
        return "configuration";
    }

    @Override
    protected UnknownDomainObjectException createNotFoundException(final String name) {
        return new UnknownConfigurationException(String.format("Configuration with name '%s' not found.", name));
    }

    @Override
    public ConfigurationInternal detachedConfiguration(final Dependency... dependencies) {
        final String name = DETACHED_CONFIGURATION_DEFAULT_NAME + this.detachedConfigurationDefaultNameCounter++;
        final DetachedConfigurationsProvider detachedConfigurationsProvider = new DetachedConfigurationsProvider();
        final DefaultConfiguration detachedConfiguration = new DefaultConfiguration(name, name, detachedConfigurationsProvider, this.resolver,
                this.listenerManager, this.dependencyMetaDataProvider, new DefaultResolutionStrategy());
        final DomainObjectSet<Dependency> detachedDependencies = detachedConfiguration.getDependencies();
        for (final Dependency dependency: dependencies) {
            detachedDependencies.add(dependency.copy());
        }
        detachedConfigurationsProvider.setTheOnlyConfiguration(detachedConfiguration);
        return detachedConfiguration;
    }

    /**
     * Build a formatted representation of all Configurations in this
     * ConfigurationContainer. Configuration(s) being toStringed are likely
     * derivations of DefaultConfiguration.
     */
    public String dump() {
        final StringBuilder reply = new StringBuilder();

        reply.append("Configuration of type: " + getTypeDisplayName());
        final Collection<Configuration> configs = getAll();
        for (final Configuration c: configs) {
            reply.append("\n  " + c.toString());
        }

        return reply.toString();
    }

    public void ensureProjectIsEvaluated(final String projectPath) {
        this.context.ensureObjectEvaluated(projectPath);
    }
}
