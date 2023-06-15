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
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.UnknownConfigurationException;
import org.gradle.api.internal.AbstractValidatingNamedDomainObjectContainer;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.DefaultRootComponentMetadataBuilder;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.RootComponentMetadataBuilder;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.reflect.Instantiator;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class DefaultConfigurationContainer extends AbstractValidatingNamedDomainObjectContainer<Configuration> implements ConfigurationContainerInternal, ConfigurationsProvider {
    public static final String DETACHED_CONFIGURATION_DEFAULT_NAME = "detachedConfiguration";
    private static final Pattern RESERVED_NAMES_FOR_DETACHED_CONFS = Pattern.compile(DETACHED_CONFIGURATION_DEFAULT_NAME + "\\d*");

    @SuppressWarnings("deprecation")
    private static final ConfigurationRole DEFAULT_ROLE_TO_CREATE = ConfigurationRoles.LEGACY;
    private static final boolean DEFAULT_LOCK_USAGE_AT_CREATION = false;

    private final AtomicInteger detachedConfigurationDefaultNameCounter = new AtomicInteger(1);
    private final Factory<ResolutionStrategyInternal> resolutionStrategyFactory;
    private final DefaultRootComponentMetadataBuilder rootComponentMetadataBuilder;
    private final DefaultConfigurationFactory defaultConfigurationFactory;

    public DefaultConfigurationContainer(
        Instantiator instantiator,
        CollectionCallbackActionDecorator callbackDecorator,
        DefaultRootComponentMetadataBuilder.Factory rootComponentMetadataBuilderFactory,
        DefaultConfigurationFactory defaultConfigurationFactory,
        ResolutionStrategyFactory resolutionStrategyFactory
    ) {
        super(Configuration.class, instantiator, new Configuration.Namer(), callbackDecorator);

        this.rootComponentMetadataBuilder = rootComponentMetadataBuilderFactory.create(this);
        this.defaultConfigurationFactory = defaultConfigurationFactory;
        this.resolutionStrategyFactory = resolutionStrategyFactory;
        this.getEventRegister().registerLazyAddAction(x -> rootComponentMetadataBuilder.getValidator().validateMutation(MutationValidator.MutationType.HIERARCHY));
        this.whenObjectRemoved(x -> rootComponentMetadataBuilder.getValidator().validateMutation(MutationValidator.MutationType.HIERARCHY));
    }

    @Override
    protected Configuration doCreate(String name) {
        return doCreate(name, DEFAULT_ROLE_TO_CREATE, DEFAULT_LOCK_USAGE_AT_CREATION);
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
        @SuppressWarnings("deprecation")
        DefaultConfiguration detachedConfiguration = newConfiguration(name, detachedConfigurationsProvider, rootComponentMetadataBuilder.withConfigurationsProvider(detachedConfigurationsProvider), ConfigurationRolesForMigration.LEGACY_TO_RESOLVABLE_BUCKET, false);
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
        validateNameIsAllowed(name);
        DefaultConfiguration configuration = newConfiguration(name, this, rootComponentMetadataBuilder, role, lockUsage);
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
