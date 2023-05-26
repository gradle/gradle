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
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConsumableConfiguration;
import org.gradle.api.artifacts.DependenciesConfiguration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolvableConfiguration;
import org.gradle.api.artifacts.UnknownConfigurationException;
import org.gradle.api.internal.AbstractValidatingNamedDomainObjectContainer;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.DefaultRootComponentMetadataBuilder;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.RootComponentMetadataBuilder;
import org.gradle.internal.Actions;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.GradleVersion;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Pattern;

// TODO: We should eventually consider making the DefaultConfigurationContainer extend DefaultPolymorphicDomainObjectContainer
public class DefaultConfigurationContainer extends AbstractValidatingNamedDomainObjectContainer<Configuration> implements ConfigurationContainerInternal, ConfigurationsProvider {
    public static final String DETACHED_CONFIGURATION_DEFAULT_NAME = "detachedConfiguration";
    private static final Pattern RESERVED_NAMES_FOR_DETACHED_CONFS = Pattern.compile(DETACHED_CONFIGURATION_DEFAULT_NAME + "\\d*");

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
    @SuppressWarnings("deprecation")
    protected Configuration doCreate(String name) {
        // TODO: Deprecate legacy configurations for consumption
        return doCreate(name, DefaultLegacyConfiguration.class, ConfigurationRoles.LEGACY, false);
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
    public NamedDomainObjectProvider<ResolvableConfiguration> resolvable(String name) {
        assertMutable("resolvable(String)");
        return registerConfiguration(name, ConfigurationRoles.RESOLVABLE, true, Actions.doNothing(), DefaultResolvableConfiguration.class);
    }

    @Override
    public NamedDomainObjectProvider<ResolvableConfiguration> resolvable(String name, Action<? super ResolvableConfiguration> action) {
        assertMutable("resolvableUnlocked(String, Action)");
        return registerConfiguration(name, ConfigurationRoles.RESOLVABLE, true, action, DefaultResolvableConfiguration.class);
    }

    @Override
    public NamedDomainObjectProvider<ResolvableConfiguration> resolvableUnlocked(String name) {
        assertMutable("resolvableUnlocked(String)");
        return registerConfiguration(name, ConfigurationRoles.RESOLVABLE, false, Actions.doNothing(), DefaultResolvableConfiguration.class);
    }

    @Override
    public NamedDomainObjectProvider<ResolvableConfiguration> resolvableUnlocked(String name, Action<? super ResolvableConfiguration> action) {
        assertMutable("resolvableUnlocked(String, Action)");
        return registerConfiguration(name, ConfigurationRoles.RESOLVABLE, false, action, DefaultResolvableConfiguration.class);
    }

    @Override
    public NamedDomainObjectProvider<ConsumableConfiguration> consumable(String name) {
        assertMutable("consumable(String)");
        return registerConfiguration(name, ConfigurationRoles.CONSUMABLE, true, Actions.doNothing(), DefaultConsumableConfiguration.class);
    }

    @Override
    public NamedDomainObjectProvider<ConsumableConfiguration> consumable(String name, Action<? super ConsumableConfiguration> action) {
        assertMutable("consumable(String, Action)");
        return registerConfiguration(name, ConfigurationRoles.CONSUMABLE, true, action, DefaultConsumableConfiguration.class);
    }

    @Override
    public NamedDomainObjectProvider<ConsumableConfiguration> consumableUnlocked(String name) {
        assertMutable("consumableUnlocked(String)");
        return registerConfiguration(name, ConfigurationRoles.CONSUMABLE, false, Actions.doNothing(), DefaultConsumableConfiguration.class);
    }

    @Override
    public NamedDomainObjectProvider<ConsumableConfiguration> consumableUnlocked(String name, Action<? super ConsumableConfiguration> action) {
        assertMutable("consumableUnlocked(String, Action)");
        return registerConfiguration(name, ConfigurationRoles.CONSUMABLE, false, action, DefaultConsumableConfiguration.class);
    }

    @Override
    public NamedDomainObjectProvider<DependenciesConfiguration> dependencies(String name) {
        assertMutable("dependencies(String)");
        return this.registerConfiguration(name, ConfigurationRoles.BUCKET, true, Actions.doNothing(), DefaultDependenciesConfiguration.class);
    }

    @Override
    public NamedDomainObjectProvider<DependenciesConfiguration> dependencies(String name, Action<? super DependenciesConfiguration> action) {
        assertMutable("dependencies(String, Action)");
        return registerConfiguration(name, ConfigurationRoles.BUCKET, true, action, DefaultDependenciesConfiguration.class);
    }

    @Override
    public NamedDomainObjectProvider<DependenciesConfiguration> dependenciesUnlocked(String name) {
        assertMutable("dependenciesUnlocked(String)");
        return registerConfiguration(name, ConfigurationRoles.BUCKET, false, Actions.doNothing(), DefaultDependenciesConfiguration.class);
    }

    @Override
    public NamedDomainObjectProvider<DependenciesConfiguration> dependenciesUnlocked(String name, Action<? super DependenciesConfiguration> action) {
        assertMutable("dependenciesUnlocked(String, Action)");
        return registerConfiguration(name, ConfigurationRoles.BUCKET, false, action, DefaultDependenciesConfiguration.class);
    }

    @Override
    @Deprecated
    public NamedDomainObjectProvider<Configuration> resolvableDependenciesUnlocked(String name) {
        assertMutable("resolvableDependenciesUnlocked(String)");
        return registerConfiguration(name, ConfigurationRoles.RESOLVABLE_BUCKET, false, Actions.doNothing(), DefaultResolvableDependenciesConfiguration.class);
    }

    @Override
    @Deprecated
    public NamedDomainObjectProvider<Configuration> resolvableDependenciesUnlocked(String name, Action<? super Configuration> action) {
        assertMutable("resolvableDependenciesUnlocked(String, Action)");
        return registerConfiguration(name, ConfigurationRoles.RESOLVABLE_BUCKET, false, action, DefaultResolvableDependenciesConfiguration.class);
    }

    public NamedDomainObjectProvider<Configuration> migratingUnlocked(String name, ConfigurationRole role) {
        assertMutable("migratingUnlocked(String, ConfigurationRole)");
        return migratingUnlocked(name, role, Actions.doNothing());
    }

    @Override
    @SuppressWarnings("deprecation")
    public NamedDomainObjectProvider<Configuration> migratingUnlocked(String name, ConfigurationRole role, Action<? super Configuration> action) {
        assertMutable("migratingUnlocked(String, ConfigurationRole, Action)");

        Class<? extends DefaultConfiguration> configurationType;
        if (role == ConfigurationRolesForMigration.LEGACY_TO_RESOLVABLE_BUCKET || role == ConfigurationRolesForMigration.LEGACY_TO_CONSUMABLE) {
            configurationType = DefaultLegacyConfiguration.class;
        } else if (role == ConfigurationRolesForMigration.RESOLVABLE_BUCKET_TO_RESOLVABLE) {
            configurationType = DefaultResolvableDependenciesConfiguration.class;
        } else if (role == ConfigurationRolesForMigration.CONSUMABLE_BUCKET_TO_CONSUMABLE) {
            configurationType = DefaultConsumableDependenciesConfiguration.class;
        } else {
            throw new InvalidUserDataException("Unknown migration role: " + role);
        }

        return registerConfiguration(name, role, false, action, configurationType);
    }

    @Override
    public NamedDomainObjectProvider<? extends Configuration> maybeRegisterResolvableUnlocked(String name, Action<? super Configuration> action) {
        if (!hasWithName(name)) {
            return resolvableUnlocked(name, action);
        }

        emitConfigurationExistsDeprecation(name);
        return named(name, action);
    }

    @Override
    public NamedDomainObjectProvider<? extends Configuration> maybeRegisterConsumableUnlocked(String name, Action<? super Configuration> action) {
        if (!hasWithName(name)) {
            return consumableUnlocked(name, action);
        }

        emitConfigurationExistsDeprecation(name);
        return named(name, action);
    }

    @Override
    public NamedDomainObjectProvider<? extends Configuration> maybeRegisterDependenciesUnlocked(String name, Action<? super Configuration> action) {
        return maybeRegisterDependenciesUnlocked(name, true, action);
    }

    @Override
    public NamedDomainObjectProvider<? extends Configuration> maybeRegisterDependenciesUnlocked(String name, boolean warnOnDuplicate, Action<? super Configuration> action) {
        if (!hasWithName(name)) {
            return dependenciesUnlocked(name, action);
        }

        if (warnOnDuplicate) {
            emitConfigurationExistsDeprecation(name);
        }
        return named(name, action);
    }

    @Override
    public NamedDomainObjectProvider<? extends Configuration> maybeRegisterMigratingUnlocked(String name, ConfigurationRole role, Action<? super Configuration> action) {
        if (!hasWithName(name)) {
            return migratingUnlocked(name, role, action);
        }

        emitConfigurationExistsDeprecation(name);
        return named(name, action);
    }

    @Override
    @Deprecated
    public NamedDomainObjectProvider<? extends Configuration> maybeRegisterResolvableDependenciesUnlocked(String name, Action<? super Configuration> action) {
        if (!hasWithName(name)) {
            return resolvableDependenciesUnlocked(name, action);
        }

        emitConfigurationExistsDeprecation(name);
        return named(name, action);
    }

    private <T extends Configuration> NamedDomainObjectProvider<T> registerConfiguration(String name, ConfigurationRole role, boolean lockUsage, Action<? super T> configureAction, Class<? extends T> configurationType) {
        // Sanity check to make sure we are locking all non-legacy configurations by 9.0
        assert lockUsage || GradleVersion.current().getBaseVersion().compareTo(GradleVersion.version("9.0")) < 0;

        // TODO: Deprecate changing roles of unlocked non-legacy configurations.

        assertCanAdd(name);
        NamedDomainObjectProvider<T> configuration = Cast.uncheckedCast(
            getInstantiator().newInstance(NamedDomainObjectCreatingProvider.class, this, name, configurationType, configureAction, role, lockUsage));
        addLater(configuration);
        return configuration;
    }

    // Cannot be private due to reflective instantiation
    public class NamedDomainObjectCreatingProvider<I extends DefaultConfiguration> extends AbstractDomainObjectCreatingProvider<I> {

        private final ConfigurationRole role;
        private final boolean lockUsage;

        public NamedDomainObjectCreatingProvider(String name, Class<I> type, @Nullable Action<? super I> configureAction, ConfigurationRole role, boolean lockUsage) {
            super(name, type, configureAction);
            this.role = role;
            this.lockUsage = lockUsage;
        }

        @Override
        protected I createDomainObject() {
            return doCreate(getName(), getType(), role, lockUsage);
        }
    }

    private <T extends DefaultConfiguration> T doCreate(String name, Class<T> configurationType, ConfigurationRole role, boolean lockUsage) {
        validateNameIsAllowed(name);
        T configuration = defaultConfigurationFactory.create(name, configurationType, this, resolutionStrategyFactory, rootComponentMetadataBuilder, role, lockUsage);
        configuration.addMutationValidator(rootComponentMetadataBuilder.getValidator());
        return configuration;
    }

    private static void emitConfigurationExistsDeprecation(String configurationName) {
        DeprecationLogger.deprecateBehaviour("The configuration " + configurationName + " was created explicitly. This configuration name is reserved for creation by Gradle.")
            .withAdvice("Do not create a configuration with this name.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "configurations_allowed_usage")
            .nagUser();
    }

    private static void validateNameIsAllowed(String name) {
        if (RESERVED_NAMES_FOR_DETACHED_CONFS.matcher(name).matches()) {
            DeprecationLogger.deprecateAction("Creating a configuration with a name that starts with 'detachedConfiguration'")
                    .withAdvice(String.format("Use a different name for the configuration '%s'.", name))
                    .willBeRemovedInGradle9()
                    .withUpgradeGuideSection(8, "reserved_configuration_names")
                    .nagUser();
        }
    }
}
