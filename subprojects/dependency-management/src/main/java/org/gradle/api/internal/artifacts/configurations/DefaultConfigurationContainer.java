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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConsumableConfiguration;
import org.gradle.api.artifacts.DependenciesConfiguration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolvableConfiguration;
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
import org.gradle.internal.Actions;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.util.GradleVersion;
import org.gradle.vcs.internal.VcsMappingsStore;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class DefaultConfigurationContainer extends AbstractValidatingNamedDomainObjectContainer<Configuration> implements ConfigurationContainerInternal, ConfigurationsProvider {
    public static final String DETACHED_CONFIGURATION_DEFAULT_NAME = "detachedConfiguration";
    private static final Pattern RESERVED_NAMES_FOR_DETACHED_CONFS = Pattern.compile(DETACHED_CONFIGURATION_DEFAULT_NAME + "\\d*");

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
        this.getEventRegister().registerLazyAddAction(x -> rootComponentMetadataBuilder.getValidator().validateMutation(MutationValidator.MutationType.HIERARCHY));
        this.whenObjectRemoved(x -> rootComponentMetadataBuilder.getValidator().validateMutation(MutationValidator.MutationType.HIERARCHY));
    }

    @Override
    @SuppressWarnings("deprecation")
    protected Configuration doCreate(String name) {
        return doCreate(name, ConfigurationRoles.LEGACY, false, DefaultLegacyConfiguration.class);
    }

    @Override
    public Set<? extends ConfigurationInternal> getAll() {
        Set<? extends ConfigurationInternal> set = Cast.uncheckedCast(this);
        return ImmutableSet.copyOf(set);
    }

    @Override
    public void visitAll(Consumer<ConfigurationInternal> visitor) {
        for (Configuration configuration : this) {
            visitor.accept((ConfigurationInternal) configuration);
        }
    }

    @Override
    public ConfigurationInternal findByName(String name) {
        return (ConfigurationInternal) super.findByName(name);
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
        RootComponentMetadataBuilder componentMetadataBuilder = rootComponentMetadataBuilder.withConfigurationsProvider(detachedConfigurationsProvider);

        @SuppressWarnings("deprecation")
        DefaultConfiguration detachedConfiguration = defaultConfigurationFactory.create(
            name,
            DefaultLegacyConfiguration.class,
            detachedConfigurationsProvider,
            resolutionStrategyFactory,
            componentMetadataBuilder,
            ConfigurationRolesForMigration.LEGACY_TO_RESOLVABLE_BUCKET,
            false
        );

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

    @Override
    public ResolvableConfiguration resolvable(String name) {
        assertMutable("resolvable(String)");
        return createAndAdd(name, ConfigurationRoles.RESOLVABLE, true, Actions.doNothing(), DefaultResolvableConfiguration.class);
    }

    @Override
    public ResolvableConfiguration resolvable(String name, Action<? super ResolvableConfiguration> action) {
        assertMutable("resolvableUnlocked(String, Action)");
        return createAndAdd(name, ConfigurationRoles.RESOLVABLE, true, action, DefaultResolvableConfiguration.class);
    }

    @Override
    public ResolvableConfiguration resolvableUnlocked(String name) {
        assertMutable("resolvableUnlocked(String)");
        return createAndAdd(name, ConfigurationRoles.RESOLVABLE, false, Actions.doNothing(), DefaultResolvableConfiguration.class);
    }

    @Override
    public ConsumableConfiguration consumable(String name) {
        assertMutable("consumable(String)");
        return createAndAdd(name, ConfigurationRoles.CONSUMABLE, true, Actions.doNothing(), DefaultConsumableConfiguration.class);
    }

    @Override
    public ConsumableConfiguration consumable(String name, Action<? super ConsumableConfiguration> action) {
        assertMutable("consumable(String, Action)");
        return createAndAdd(name, ConfigurationRoles.CONSUMABLE, true, action, DefaultConsumableConfiguration.class);
    }

    @Override
    public ConsumableConfiguration consumableUnlocked(String name) {
        assertMutable("consumableUnlocked(String)");
        return createAndAdd(name, ConfigurationRoles.CONSUMABLE, false, Actions.doNothing(), DefaultConsumableConfiguration.class);
    }

    @Override
    public DependenciesConfiguration dependencies(String name) {
        assertMutable("dependencies(String)");
        return createAndAdd(name, ConfigurationRoles.BUCKET, true, Actions.doNothing(), DefaultDependenciesConfiguration.class);
    }

    @Override
    public DependenciesConfiguration dependencies(String name, Action<? super DependenciesConfiguration> action) {
        assertMutable("dependencies(String, Action)");
        return createAndAdd(name, ConfigurationRoles.BUCKET, true, action, DefaultDependenciesConfiguration.class);
    }

    @Override
    public DependenciesConfiguration dependenciesUnlocked(String name) {
        assertMutable("dependenciesUnlocked(String)");
        return createAndAdd(name, ConfigurationRoles.BUCKET, false, Actions.doNothing(), DefaultDependenciesConfiguration.class);
    }

    @Override
    @Deprecated
    public Configuration resolvableDependenciesUnlocked(String name) {
        assertMutable("resolvableDependenciesUnlocked(String)");
        return createAndAdd(name, ConfigurationRoles.RESOLVABLE_BUCKET, false, Actions.doNothing(), DefaultResolvableDependenciesConfiguration.class);
    }

    @SuppressWarnings("deprecation")
    public Configuration migratingUnlocked(String name, ConfigurationRole role) {
        assertMutable("migratingUnlocked(String, ConfigurationRole)");

        Class<? extends DefaultConfiguration> configurationType;
        if (role == ConfigurationRolesForMigration.LEGACY_TO_RESOLVABLE_BUCKET || role == ConfigurationRolesForMigration.LEGACY_TO_CONSUMABLE) {
            configurationType = DefaultLegacyConfiguration.class;
        } else if (role == ConfigurationRolesForMigration.RESOLVABLE_BUCKET_TO_RESOLVABLE) {
            configurationType = DefaultResolvableDependenciesConfiguration.class;
        } else if (role == ConfigurationRolesForMigration.CONSUMABLE_BUCKET_TO_CONSUMABLE) {
            configurationType = DefaultConsumableDependenciesConfiguration.class;
        } else {
            throw new UnsupportedOperationException("Unknown migration role: " + role);
        }

        return createAndAdd(name, role, false, Actions.doNothing(), configurationType);
    }

    private <T extends DefaultConfiguration> T createAndAdd(String name, ConfigurationRole role, boolean lockUsage, Action<? super T> configureAction, Class<T> configurationType) {
        assertCanAdd(name);
        T configuration = doCreate(name, role, lockUsage, configurationType);
        add(configuration);
        configureAction.execute(configuration);
        return configuration;
    }

    private <T extends DefaultConfiguration> T doCreate(String name, ConfigurationRole role, boolean lockUsage, Class<T> configurationType) {
        // Sanity check to make sure we are locking all configurations by 9.0
        assert lockUsage || GradleVersion.current().getBaseVersion().compareTo(GradleVersion.version("9.0")) < 0;

        validateNameIsAllowed(name);
        T configuration = defaultConfigurationFactory.create(name, configurationType, this, resolutionStrategyFactory, rootComponentMetadataBuilder, role, lockUsage);
        configuration.addMutationValidator(rootComponentMetadataBuilder.getValidator());
        return configuration;
    }

    private void validateNameIsAllowed(String name) {
        if (RESERVED_NAMES_FOR_DETACHED_CONFS.matcher(name).matches()) {
            DeprecationLogger.deprecateAction("Creating a configuration with a name that starts with 'detachedConfiguration'")
                    .withAdvice(String.format("Use a different name for the configuration '%s'.", name))
                    .willBeRemovedInGradle9()
                    .withUpgradeGuideSection(8, "reserved_configuration_names")
                    .nagUser();
        }
    }
}
