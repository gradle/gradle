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

import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.UnknownConfigurationException;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.AbstractValidatingNamedDomainObjectContainer;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.artifacts.ComponentSelectorConverter;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory;
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParserFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionRules;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.DefaultRootComponentMetadataBuilder;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.RootComponentMetadataBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.CapabilitiesResolutionInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultCapabilitiesResolution;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultResolutionStrategy;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.notations.ComponentIdentifierParserFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.vcs.internal.VcsMappingsStore;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class DefaultConfigurationContainer extends AbstractValidatingNamedDomainObjectContainer<Configuration>
    implements ConfigurationContainerInternal, ConfigurationsProvider {
    public static final String DETACHED_CONFIGURATION_DEFAULT_NAME = "detachedConfiguration";

    @SuppressWarnings("deprecation")
    private static final ConfigurationRole DEFAULT_ROLE_TO_CREATE = ConfigurationRoles.LEGACY;
    private static final boolean DEFAULT_LOCK_USAGE_AT_CREATION = false;

    private final AtomicInteger detachedConfigurationDefaultNameCounter = new AtomicInteger(1);
    private final Factory<ResolutionStrategyInternal> resolutionStrategyFactory;
    private final DefaultRootComponentMetadataBuilder rootComponentMetadataBuilder;
    private final DefaultConfigurationFactory defaultConfigurationFactory;

    public DefaultConfigurationContainer(
        Instantiator instantiator,
        DependencySubstitutionRules globalDependencySubstitutionRules,
        VcsMappingsStore vcsMappingsStore,
        ComponentIdentifierFactory componentIdentifierFactory,
        ImmutableAttributesFactory attributesFactory,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        ComponentSelectorConverter componentSelectorConverter,
        DependencyLockingProvider dependencyLockingProvider,
        CollectionCallbackActionDecorator callbackDecorator,
        NotationParser<Object, ComponentSelector> moduleSelectorNotationParser,
        ObjectFactory objectFactory,
        DefaultRootComponentMetadataBuilder.Factory rootComponentMetadataBuilderFactory,
        DefaultConfigurationFactory defaultConfigurationFactory
    ) {
        super(Configuration.class, instantiator, new Configuration.Namer(), callbackDecorator);
        NotationParser<Object, Capability> dependencyCapabilityNotationParser = new CapabilityNotationParserFactory(false).create();
        resolutionStrategyFactory = () -> {
            CapabilitiesResolutionInternal capabilitiesResolutionInternal = instantiator.newInstance(DefaultCapabilitiesResolution.class, new CapabilityNotationParserFactory(false).create(), new ComponentIdentifierParserFactory().create());
            return instantiator.newInstance(DefaultResolutionStrategy.class, globalDependencySubstitutionRules, vcsMappingsStore, componentIdentifierFactory, moduleIdentifierFactory, componentSelectorConverter, dependencyLockingProvider, capabilitiesResolutionInternal, instantiator, objectFactory, attributesFactory, moduleSelectorNotationParser, dependencyCapabilityNotationParser);
        };
        this.rootComponentMetadataBuilder = rootComponentMetadataBuilderFactory.create(this);
        this.defaultConfigurationFactory = defaultConfigurationFactory;
        this.getEventRegister().registerLazyAddAction(x -> rootComponentMetadataBuilder.discardAll());
        this.whenObjectRemoved(x -> rootComponentMetadataBuilder.discardAll());
    }

    @Override
    protected Configuration doCreate(String name) {
        return doCreate(name, DEFAULT_ROLE_TO_CREATE, DEFAULT_LOCK_USAGE_AT_CREATION);
    }

    @Override
    public Set<? extends ConfigurationInternal> getAll() {
        return stream().map(ConfigurationInternal.class::cast).collect(Collectors.toCollection(LinkedHashSet::new));
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

    @Override
    public ConfigurationInternal detachedConfiguration(Dependency... dependencies) {
        String name = nextDetachedConfigurationName();
        DetachedConfigurationsProvider detachedConfigurationsProvider = new DetachedConfigurationsProvider();
        @SuppressWarnings("deprecation")
        DefaultConfiguration detachedConfiguration = newConfiguration(name, detachedConfigurationsProvider, rootComponentMetadataBuilder.withConfigurationsProvider(detachedConfigurationsProvider), ConfigurationRoles.LEGACY, false);
        copyAllTo(detachedConfiguration, dependencies);
        detachedConfigurationsProvider.setTheOnlyConfiguration(detachedConfiguration);
        return detachedConfiguration;
    }

    private String nextDetachedConfigurationName() {
        return DETACHED_CONFIGURATION_DEFAULT_NAME + detachedConfigurationDefaultNameCounter.getAndIncrement();
    }

    private void copyAllTo(DefaultConfiguration detachedConfiguration, Dependency[] dependencies) {
        DomainObjectSet<Dependency> detachedDependencies = detachedConfiguration.getDependencies();
        for (Dependency dependency : dependencies) {
            detachedDependencies.add(dependency.copy());
        }
    }

    private DefaultConfiguration newConfiguration(String name,
                                                  ConfigurationsProvider detachedConfigurationsProvider,
                                                  RootComponentMetadataBuilder componentMetadataBuilder,
                                                  ConfigurationRole role,
                                                  boolean lockUsage) {
        return defaultConfigurationFactory.create(name, detachedConfigurationsProvider, resolutionStrategyFactory, componentMetadataBuilder, role, lockUsage);
    }

    /**
     * Build a formatted representation of all Configurations in this ConfigurationContainer. Configuration(s) being toStringed are likely derivations of DefaultConfiguration.
     */
    public String dump() {
        StringBuilder reply = new StringBuilder();

        reply.append("Configuration of type: ").append(getTypeDisplayName());
        Collection<? extends Configuration> configs = getAll();
        for (Configuration c : configs) {
            reply.append("\n  ").append(c.toString());
        }

        return reply.toString();
    }

    @Override
    public Configuration createWithRole(String name, ConfigurationRole role, boolean lockUsage, Action<? super Configuration> configureAction) {
        assertMutable("createWithRole(String, ConfigurationRole, boolean, Action)");
        assertCanAdd(name);
        ConfigurationInternal object = doCreate(name, role, lockUsage);
        add(object);
        configureAction.execute(object);
        return object;
    }

    private ConfigurationInternal doCreate(String name, ConfigurationRole role, boolean lockUsage) {
        DefaultConfiguration configuration = newConfiguration(name, this, rootComponentMetadataBuilder, role, lockUsage);
        configuration.addMutationValidator(rootComponentMetadataBuilder.getValidator());
        return configuration;
    }
}
