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
import org.gradle.api.artifacts.DependencyScopeConfiguration;
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
import java.util.function.Function;
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
        validateNameIsAllowed(name);
        return defaultConfigurationFactory.create(name, this, resolutionStrategyFactory, rootComponentMetadataBuilder, ConfigurationRoles.LEGACY);
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
        DefaultUnlockedConfiguration detachedConfiguration = defaultConfigurationFactory.create(
            name,
            detachedConfigurationsProvider,
            resolutionStrategyFactory,
            componentMetadataBuilder,
            ConfigurationRolesForMigration.LEGACY_TO_RESOLVABLE_DEPENDENCY_SCOPE
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
        return registerResolvableConfiguration(name, Actions.doNothing());
    }

    @Override
    public NamedDomainObjectProvider<ResolvableConfiguration> resolvable(String name, Action<? super ResolvableConfiguration> action) {
        assertMutable("resolvableUnlocked(String, Action)");
        return registerResolvableConfiguration(name, action);
    }

    @Override
    public NamedDomainObjectProvider<Configuration> resolvableUnlocked(String name) {
        assertMutable("resolvableUnlocked(String)");
        return registerUnlockedConfiguration(name, ConfigurationRoles.RESOLVABLE, Actions.doNothing());
    }

    @Override
    public NamedDomainObjectProvider<Configuration> resolvableUnlocked(String name, Action<? super Configuration> action) {
        assertMutable("resolvableUnlocked(String, Action)");
        return registerUnlockedConfiguration(name, ConfigurationRoles.RESOLVABLE, action);
    }

    @Override
    public NamedDomainObjectProvider<ConsumableConfiguration> consumable(String name) {
        assertMutable("consumable(String)");
        return registerConsumableConfiguration(name, Actions.doNothing());
    }

    @Override
    public NamedDomainObjectProvider<ConsumableConfiguration> consumable(String name, Action<? super ConsumableConfiguration> action) {
        assertMutable("consumable(String, Action)");
        return registerConsumableConfiguration(name, action);
    }

    @Override
    public NamedDomainObjectProvider<Configuration> consumableUnlocked(String name) {
        assertMutable("consumableUnlocked(String)");
        return registerUnlockedConfiguration(name, ConfigurationRoles.CONSUMABLE, Actions.doNothing());
    }

    @Override
    public NamedDomainObjectProvider<Configuration> consumableUnlocked(String name, Action<? super Configuration> action) {
        assertMutable("consumableUnlocked(String, Action)");
        return registerUnlockedConfiguration(name, ConfigurationRoles.CONSUMABLE, action);
    }

    @Override
    public NamedDomainObjectProvider<DependencyScopeConfiguration> dependencyScope(String name) {
        assertMutable("dependencyScope(String)");
        return registerDependencyScopeConfiguration(name, Actions.doNothing());
    }

    @Override
    public NamedDomainObjectProvider<DependencyScopeConfiguration> dependencyScope(String name, Action<? super DependencyScopeConfiguration> action) {
        assertMutable("dependencyScope(String, Action)");
        return registerDependencyScopeConfiguration(name, action);
    }

    @Override
    public NamedDomainObjectProvider<Configuration> dependencyScopeUnlocked(String name) {
        assertMutable("dependencyScopeUnlocked(String)");
        return registerUnlockedConfiguration(name, ConfigurationRoles.DEPENDENCY_SCOPE, Actions.doNothing());
    }

    @Override
    public NamedDomainObjectProvider<Configuration> dependencyScopeUnlocked(String name, Action<? super Configuration> action) {
        assertMutable("dependencyScopeUnlocked(String, Action)");
        return registerUnlockedConfiguration(name, ConfigurationRoles.DEPENDENCY_SCOPE, action);
    }

    @Override
    @Deprecated
    public NamedDomainObjectProvider<Configuration> resolvableDependencyScopeUnlocked(String name) {
        assertMutable("resolvableDependencyScopeUnlocked(String)");
        return registerUnlockedConfiguration(name, ConfigurationRoles.RESOLVABLE_DEPENDENCY_SCOPE, Actions.doNothing());
    }

    @Override
    @Deprecated
    public NamedDomainObjectProvider<Configuration> resolvableDependencyScopeUnlocked(String name, Action<? super Configuration> action) {
        assertMutable("resolvableDependencyScopeUnlocked(String, Action)");
        return registerUnlockedConfiguration(name, ConfigurationRoles.RESOLVABLE_DEPENDENCY_SCOPE, action);
    }

    public NamedDomainObjectProvider<Configuration> migratingUnlocked(String name, ConfigurationRole role) {
        assertMutable("migratingUnlocked(String, ConfigurationRole)");
        return migratingUnlocked(name, role, Actions.doNothing());
    }

    @Override
    public NamedDomainObjectProvider<Configuration> migratingUnlocked(String name, ConfigurationRole role, Action<? super Configuration> action) {
        assertMutable("migratingUnlocked(String, ConfigurationRole, Action)");

        if (!ConfigurationRolesForMigration.ALL.contains(role)) {
            throw new InvalidUserDataException("Unknown migration role: " + role);
        }

        return registerUnlockedConfiguration(name, role, action);
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
    public NamedDomainObjectProvider<? extends Configuration> maybeRegisterDependencyScopeUnlocked(String name, Action<? super Configuration> action) {
        return maybeRegisterDependencyScopeUnlocked(name, true, action);
    }

    @Override
    public NamedDomainObjectProvider<? extends Configuration> maybeRegisterDependencyScopeUnlocked(String name, boolean warnOnDuplicate, Action<? super Configuration> action) {
        if (!hasWithName(name)) {
            return dependencyScopeUnlocked(name, action);
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
    public NamedDomainObjectProvider<? extends Configuration> maybeRegisterResolvableDependencyScopeUnlocked(String name, Action<? super Configuration> action) {
        if (!hasWithName(name)) {
            return resolvableDependencyScopeUnlocked(name, action);
        }

        emitConfigurationExistsDeprecation(name);
        return named(name, action);
    }

    private NamedDomainObjectProvider<ConsumableConfiguration> registerConsumableConfiguration(String name, Action<? super ConsumableConfiguration> configureAction) {
        return registerConfiguration(name, configureAction, ConsumableConfiguration.class, n ->
            defaultConfigurationFactory.createConsumable(name, this, resolutionStrategyFactory, rootComponentMetadataBuilder)
        );
    }

    private NamedDomainObjectProvider<ResolvableConfiguration> registerResolvableConfiguration(String name, Action<? super ResolvableConfiguration> configureAction) {
        return registerConfiguration(name, configureAction, ResolvableConfiguration.class, n ->
            defaultConfigurationFactory.createResolvable(name, this, resolutionStrategyFactory, rootComponentMetadataBuilder)
        );
    }

    private NamedDomainObjectProvider<DependencyScopeConfiguration> registerDependencyScopeConfiguration(String name, Action<? super DependencyScopeConfiguration> configureAction) {
        return registerConfiguration(name, configureAction, DependencyScopeConfiguration.class, n ->
            defaultConfigurationFactory.createDependencyScope(name, this, resolutionStrategyFactory, rootComponentMetadataBuilder)
        );
    }

    @SuppressWarnings("deprecation")
    private NamedDomainObjectProvider<Configuration> registerUnlockedConfiguration(String name, ConfigurationRole role, Action<? super Configuration> configureAction) {
        // Sanity check to make sure we are locking all non-legacy configurations by 9.0
        assert GradleVersion.current().getBaseVersion().compareTo(GradleVersion.version("9.0")) < 0 || role == ConfigurationRoles.LEGACY;

        // TODO: Deprecate changing roles of unlocked non-legacy configurations.

        return registerConfiguration(name, configureAction, Configuration.class, n ->
            defaultConfigurationFactory.create(name, this, resolutionStrategyFactory, rootComponentMetadataBuilder, role)
        );
    }

    private <T extends Configuration> NamedDomainObjectProvider<T> registerConfiguration(String name, Action<? super T> configureAction, Class<T> publicType, Function<String, T> factory) {
        assertCanAdd(name);
        validateNameIsAllowed(name);

        NamedDomainObjectProvider<T> configuration = Cast.uncheckedCast(
            getInstantiator().newInstance(NamedDomainObjectCreatingProvider.class, this, name, publicType, configureAction, factory));
        addLater(configuration);
        return configuration;
    }

    // Cannot be private due to reflective instantiation
    public class NamedDomainObjectCreatingProvider<I extends Configuration> extends AbstractDomainObjectCreatingProvider<I> {

        private final Function<String, I> factory;

        public NamedDomainObjectCreatingProvider(String name, Class<I> type, @Nullable Action<? super I> configureAction, Function<String, I> factory) {
            super(name, type, configureAction);
            this.factory = factory;
        }

        @Override
        protected I createDomainObject() {
            return factory.apply(getName());
        }
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
