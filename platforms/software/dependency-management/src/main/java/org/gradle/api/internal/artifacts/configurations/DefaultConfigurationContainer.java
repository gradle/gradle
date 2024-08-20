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
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Named;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConsumableConfiguration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.artifacts.ResolvableConfiguration;
import org.gradle.api.artifacts.UnknownConfigurationException;
import org.gradle.api.internal.AbstractValidatingNamedDomainObjectContainer;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.DefaultRootComponentMetadataBuilder;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.RootComponentMetadataBuilder;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Actions;
import org.gradle.internal.Cast;
import org.gradle.internal.artifacts.configurations.AbstractRoleBasedConfigurationCreationRequest;
import org.gradle.internal.artifacts.configurations.NoContextRoleBasedConfigurationCreationRequest;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.scopes.DetachedDependencyMetadataProvider;
import org.gradle.util.GradleVersion;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

// TODO: We should eventually consider making the DefaultConfigurationContainer extend DefaultPolymorphicDomainObjectContainer
public class DefaultConfigurationContainer extends AbstractValidatingNamedDomainObjectContainer<Configuration> implements ConfigurationContainerInternal {
    public static final String DETACHED_CONFIGURATION_DEFAULT_NAME = "detachedConfiguration";
    private static final Pattern RESERVED_NAMES_FOR_DETACHED_CONFS = Pattern.compile(DETACHED_CONFIGURATION_DEFAULT_NAME + "\\d*");
    @SuppressWarnings("deprecation")
    private static final Set<ConfigurationRole> VALID_MAYBE_CREATE_ROLES = new HashSet<>(Arrays.asList(ConfigurationRoles.CONSUMABLE, ConfigurationRoles.RESOLVABLE, ConfigurationRoles.DEPENDENCY_SCOPE, ConfigurationRoles.RESOLVABLE_DEPENDENCY_SCOPE));

    private final DependencyMetaDataProvider rootComponentIdentity;
    private final DomainObjectContext owner;
    private final DefaultConfigurationFactory defaultConfigurationFactory;
    private final ResolutionStrategyFactory resolutionStrategyFactory;

    private final AtomicInteger detachedConfigurationDefaultNameCounter = new AtomicInteger(1);
    private final RootComponentMetadataBuilder rootComponentMetadataBuilder;

    @Inject
    public DefaultConfigurationContainer(
        Instantiator instantiator,
        CollectionCallbackActionDecorator callbackDecorator,
        DependencyMetaDataProvider rootComponentIdentity,
        DomainObjectContext owner,
        AttributesSchemaInternal schema,
        DefaultRootComponentMetadataBuilder.Factory rootComponentMetadataBuilderFactory,
        DefaultConfigurationFactory defaultConfigurationFactory,
        ResolutionStrategyFactory resolutionStrategyFactory
    ) {
        super(Configuration.class, instantiator, Named.Namer.INSTANCE, callbackDecorator);

        this.rootComponentIdentity = rootComponentIdentity;
        this.owner = owner;
        this.defaultConfigurationFactory = defaultConfigurationFactory;
        this.resolutionStrategyFactory = resolutionStrategyFactory;

        this.rootComponentMetadataBuilder = rootComponentMetadataBuilderFactory.create(owner, this, rootComponentIdentity, schema);
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
    public boolean isFixedSize() {
        return false;
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
    public boolean add(Configuration o) {
        DeprecationLogger.deprecateBehaviour("Adding a configuration directly to the configuration container.")
            .withAdvice("Use a factory method instead.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "adding_to_configuration_container")
            .nagUser();
        return super.add(o);
    }

    @Override
    public boolean addAll(Collection<? extends Configuration> c) {
        DeprecationLogger.deprecateBehaviour("Adding a collection of configurations directly to the configuration container.")
            .withAdvice("Use a factory method instead.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "adding_to_configuration_container")
            .nagUser();
        return super.addAll(c);
    }

    @Override
    public void addLater(Provider<? extends Configuration> provider) {
        DeprecationLogger.deprecateBehaviour("Adding a configuration provider directly to the configuration container.")
            .withAdvice("Use a factory method instead.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "adding_to_configuration_container")
            .nagUser();
        super.addLater(provider);
    }

    @Override
    public void addAllLater(Provider<? extends Iterable<Configuration>> provider) {
        DeprecationLogger.deprecateBehaviour("Adding a provider of configurations directly to the configuration container.")
            .withAdvice("Use a factory method instead.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "adding_to_configuration_container")
            .nagUser();
        super.addAllLater(provider);
    }

    @Override
    public ConfigurationInternal detachedConfiguration(Dependency... dependencies) {
        String name = nextDetachedConfigurationName();
        DetachedConfigurationsProvider detachedConfigurationsProvider = new DetachedConfigurationsProvider();

        DependencyMetaDataProvider componentIdentity = new DetachedDependencyMetadataProvider(rootComponentIdentity, name);
        RootComponentMetadataBuilder componentMetadataBuilder = rootComponentMetadataBuilder.newBuilder(componentIdentity, detachedConfigurationsProvider);

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
        assertCanMutate("resolvable(String)");
        return registerResolvableConfiguration(name, Actions.doNothing());
    }

    @Override
    public NamedDomainObjectProvider<ResolvableConfiguration> resolvable(String name, Action<? super ResolvableConfiguration> action) {
        assertCanMutate("resolvableUnlocked(String, Action)");
        return registerResolvableConfiguration(name, action);
    }

    @Override
    public Configuration resolvableUnlocked(String name) {
        assertCanMutate("resolvableUnlocked(String)");
        return createUnlockedConfiguration(name, ConfigurationRoles.RESOLVABLE, Actions.doNothing());
    }

    @Override
    public Configuration resolvableUnlocked(String name, Action<? super Configuration> action) {
        assertCanMutate("resolvableUnlocked(String, Action)");
        return createUnlockedConfiguration(name, ConfigurationRoles.RESOLVABLE, action);
    }

    @Override
    public NamedDomainObjectProvider<ConsumableConfiguration> consumable(String name) {
        assertCanMutate("consumable(String)");
        return registerConsumableConfiguration(name, Actions.doNothing());
    }

    @Override
    public NamedDomainObjectProvider<ConsumableConfiguration> consumable(String name, Action<? super ConsumableConfiguration> action) {
        assertCanMutate("consumable(String, Action)");
        return registerConsumableConfiguration(name, action);
    }

    @Override
    public Configuration consumableUnlocked(String name) {
        assertCanMutate("consumableUnlocked(String)");
        return createUnlockedConfiguration(name, ConfigurationRoles.CONSUMABLE, Actions.doNothing());
    }

    @Override
    public Configuration consumableUnlocked(String name, Action<? super Configuration> action) {
        assertCanMutate("consumableUnlocked(String, Action)");
        return createUnlockedConfiguration(name, ConfigurationRoles.CONSUMABLE, action);
    }

    @Override
    public NamedDomainObjectProvider<DependencyScopeConfiguration> dependencyScope(String name) {
        assertCanMutate("dependencyScope(String)");
        return registerDependencyScopeConfiguration(name, Actions.doNothing());
    }

    @Override
    public NamedDomainObjectProvider<DependencyScopeConfiguration> dependencyScope(String name, Action<? super DependencyScopeConfiguration> action) {
        assertCanMutate("dependencyScope(String, Action)");
        return registerDependencyScopeConfiguration(name, action);
    }

    @Override
    public Configuration dependencyScopeUnlocked(String name) {
        assertCanMutate("dependencyScopeUnlocked(String)");
        return createUnlockedConfiguration(name, ConfigurationRoles.DEPENDENCY_SCOPE, Actions.doNothing());
    }

    @Override
    public Configuration dependencyScopeUnlocked(String name, Action<? super Configuration> action) {
        assertCanMutate("dependencyScopeUnlocked(String, Action)");
        return createUnlockedConfiguration(name, ConfigurationRoles.DEPENDENCY_SCOPE, action);
    }

    @Override
    @Deprecated
    public Configuration resolvableDependencyScopeUnlocked(String name) {
        assertCanMutate("resolvableDependencyScopeUnlocked(String)");
        return createUnlockedConfiguration(name, ConfigurationRoles.RESOLVABLE_DEPENDENCY_SCOPE, Actions.doNothing());
    }

    @Override
    @Deprecated
    public Configuration resolvableDependencyScopeUnlocked(String name, Action<? super Configuration> action) {
        assertCanMutate("resolvableDependencyScopeUnlocked(String, Action)");
        return createUnlockedConfiguration(name, ConfigurationRoles.RESOLVABLE_DEPENDENCY_SCOPE, action);
    }

    @Override
    public Configuration migratingUnlocked(String name, ConfigurationRole role) {
        assertCanMutate("migratingUnlocked(String, ConfigurationRole)");
        return migratingUnlocked(name, role, Actions.doNothing());
    }

    @Override
    public Configuration migratingUnlocked(String name, ConfigurationRole role, Action<? super Configuration> action) {
        assertCanMutate("migratingUnlocked(String, ConfigurationRole, Action)");

        if (ConfigurationRolesForMigration.ALL.contains(role)) {
            return createUnlockedConfiguration(name, role, action);
        } else {
            throw new InvalidUserDataException("Unknown migration role: " + role);
        }
    }

    @Override
    public Configuration maybeCreateResolvableUnlocked(String name) {
        return doMaybeCreate(new NoContextRoleBasedConfigurationCreationRequest(name, ConfigurationRoles.RESOLVABLE), true);
    }

    @Override
    public Configuration maybeCreateConsumableUnlocked(String name) {
        return doMaybeCreate(new NoContextRoleBasedConfigurationCreationRequest(name, ConfigurationRoles.CONSUMABLE), true);
    }

    @Override
    public Configuration maybeCreateDependencyScopeUnlocked(String name) {
        return maybeCreateDependencyScopeUnlocked(name, true);
    }

    @Override
    public Configuration maybeCreateDependencyScopeUnlocked(String name, boolean verifyPrexisting) {
        return doMaybeCreate(new NoContextRoleBasedConfigurationCreationRequest(name, ConfigurationRoles.DEPENDENCY_SCOPE), verifyPrexisting);
    }

    @Override
    public Configuration maybeCreateMigratingUnlocked(String name, ConfigurationRole role) {
        AbstractRoleBasedConfigurationCreationRequest request = new NoContextRoleBasedConfigurationCreationRequest(name, role);

        Configuration conf = findByName(request.getConfigurationName());
        if (null != conf) {
            return request.verifyExistingConfigurationUsage(conf);
        } else {
            return migratingUnlocked(request.getConfigurationName(), request.getRole());
        }
    }

    @Override
    @Deprecated
    public Configuration maybeCreateResolvableDependencyScopeUnlocked(String name) {
        return maybeCreate(new NoContextRoleBasedConfigurationCreationRequest(name, ConfigurationRoles.RESOLVABLE_DEPENDENCY_SCOPE));
    }

    @Override
    public Configuration maybeCreate(RoleBasedConfigurationCreationRequest request) {
        return doMaybeCreate(request, true);
    }

    private Configuration doMaybeCreate(RoleBasedConfigurationCreationRequest request, boolean verifyPrexisting) {
        Configuration conf = findByName(request.getConfigurationName());
        if (null != conf) {
            if (verifyPrexisting) {
                return request.verifyExistingConfigurationUsage(conf);
            } else {
                return getByName(request.getConfigurationName());
            }
        } else {
            if (VALID_MAYBE_CREATE_ROLES.contains(request.getRole())) {
                return createUnlockedConfiguration(request.getConfigurationName(), request.getRole(), Actions.doNothing());
            } else {
                throw new GradleException("Cannot maybe create invalid role: " + request.getRole());
            }
        }
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
    private Configuration createUnlockedConfiguration(String name, ConfigurationRole role, Action<? super Configuration> configureAction) {
        // Sanity check to make sure we are locking all non-legacy configurations by 9.0
        assert GradleVersion.current().getBaseVersion().compareTo(GradleVersion.version("9.0")) < 0 || role == ConfigurationRoles.LEGACY
            : "Sanity check: All non-legacy configurations must be locked by 9.0";

        // TODO: Deprecate changing roles of unlocked non-legacy configurations.

        assertElementNotPresent(name);
        validateNameIsAllowed(name);
        Configuration configuration = defaultConfigurationFactory.create(name, this, resolutionStrategyFactory, rootComponentMetadataBuilder, role);
        super.add(configuration);
        configureAction.execute(configuration);
        return configuration;
    }

    private <T extends Configuration> NamedDomainObjectProvider<T> registerConfiguration(String name, Action<? super T> configureAction, Class<T> publicType, Function<String, T> factory) {
        assertElementNotPresent(name);
        validateNameIsAllowed(name);

        NamedDomainObjectProvider<T> configuration = Cast.uncheckedCast(
            getInstantiator().newInstance(NamedDomainObjectCreatingProvider.class, this, name, publicType, configureAction, factory));
        doAddLater(configuration);
        return configuration;
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

    @Override
    public String getDisplayName() {
        return "configuration container for " + owner.getDisplayName();
    }
}
