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

import groovy.lang.Closure;
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
import org.gradle.util.DeprecationLogger;

import java.util.Collection;
import java.util.Set;

public class DefaultConfigurationContainer extends AbstractNamedDomainObjectContainer<Configuration>
        implements ConfigurationContainerInternal, ConfigurationsProvider {
    public static final String DETACHED_CONFIGURATION_DEFAULT_NAME = "detachedConfiguration";
    
    private final ConfigurationResolver resolver;
    private final Instantiator instantiator;
    private final DomainObjectContext context;
    private final ListenerManager listenerManager;
    private final DependencyMetaDataProvider dependencyMetaDataProvider;

    private int detachedConfigurationDefaultNameCounter = 1;

    public DefaultConfigurationContainer(ConfigurationResolver resolver,
                                         Instantiator instantiator, DomainObjectContext context, ListenerManager listenerManager,
                                         DependencyMetaDataProvider dependencyMetaDataProvider) {
        super(Configuration.class, instantiator, new Configuration.Namer());
        this.resolver = resolver;
        this.instantiator = instantiator;
        this.context = context;
        this.listenerManager = listenerManager;
        this.dependencyMetaDataProvider = dependencyMetaDataProvider;
    }

    @Override
    protected Configuration doCreate(String name) {
        return instantiator.newInstance(DefaultConfiguration.class, context.absoluteProjectPath(name),
                name, this, resolver, listenerManager,
                dependencyMetaDataProvider, instantiator.newInstance(DefaultResolutionStrategy.class));
    }

    public Set<Configuration> getAll() {
        return this;
    }

    public Configuration add(String name) {
        DeprecationLogger.nagUserOfReplacedMethod("ConfigurationContainer.add()", "create()");
        return create(name);
    }

    public Configuration add(String name, Closure closure) {
        DeprecationLogger.nagUserOfReplacedMethod("ConfigurationContainer.add()", "create()");
        return create(name, closure);
    }

    @Override
    public ConfigurationInternal getByName(String name) {
        return (ConfigurationInternal) super.getByName(name);
    }

    @Override
    public String getTypeDisplayName() {
        return "configuration";
    }

    @Override
    protected UnknownDomainObjectException createNotFoundException(String name) {
        return new UnknownConfigurationException(String.format("Configuration with name '%s' not found.", name));
    }

    public ConfigurationInternal detachedConfiguration(Dependency... dependencies) {
        String name = DETACHED_CONFIGURATION_DEFAULT_NAME + detachedConfigurationDefaultNameCounter++;
        DetachedConfigurationsProvider detachedConfigurationsProvider = new DetachedConfigurationsProvider();
        DefaultConfiguration detachedConfiguration = new DefaultConfiguration(
                name, name, detachedConfigurationsProvider, resolver,
                listenerManager, dependencyMetaDataProvider, new DefaultResolutionStrategy());
        DomainObjectSet<Dependency> detachedDependencies = detachedConfiguration.getDependencies();
        for (Dependency dependency : dependencies) {
            detachedDependencies.add(dependency.copy());
        }
        detachedConfigurationsProvider.setTheOnlyConfiguration(detachedConfiguration);
        return detachedConfiguration;
    }
    
    /**
     * Build a formatted representation of all Configurations in this ConfigurationContainer.
     * Configuration(s) being toStringed are likely derivations of DefaultConfiguration.
     */
    public String dump() {
        StringBuilder reply = new StringBuilder();
        
        reply.append("Configuration of type: " + getTypeDisplayName());
        Collection<Configuration> configs = getAll();
        for (Configuration c : configs) {
            reply.append("\n  " + c.toString());
        }
        
        return reply.toString();
    }
}
