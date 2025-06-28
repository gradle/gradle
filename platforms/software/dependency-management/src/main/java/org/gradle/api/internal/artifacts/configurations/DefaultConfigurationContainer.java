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
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Named;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConsumableConfiguration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.artifacts.LegacyConfiguration;
import org.gradle.api.artifacts.ResolvableConfiguration;
import org.gradle.api.artifacts.UnknownConfigurationException;
import org.gradle.api.internal.AbstractNamedDomainObjectContainer;
import org.gradle.api.internal.AbstractValidatingNamedDomainObjectContainer;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.ConfigurationResolver;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.problems.internal.ProblemsInternal;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Actions;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.Instantiator;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

// TODO: We should eventually consider making the DefaultConfigurationContainer extend DefaultPolymorphicDomainObjectContainer
public class DefaultConfigurationContainer extends AbstractValidatingNamedDomainObjectContainer<Configuration> implements ConfigurationContainerInternal {

    public static final String DETACHED_CONFIGURATION_DEFAULT_NAME = "detachedConfiguration";
    private static final Pattern RESERVED_NAMES_FOR_DETACHED_CONFS = Pattern.compile(DETACHED_CONFIGURATION_DEFAULT_NAME + "\\d*");

    private final DomainObjectContext owner;
    private final DefaultConfigurationFactory defaultConfigurationFactory;
    private final ResolutionStrategyFactory resolutionStrategyFactory;
    private final ProblemsInternal problemsService;

    private final ConfigurationResolver resolver;

    private final AtomicInteger detachedConfigurationDefaultNameCounter = new AtomicInteger(1);

    @Inject
    public DefaultConfigurationContainer(
        Instantiator instantiator,
        CollectionCallbackActionDecorator callbackDecorator,
        DomainObjectContext owner,
        DefaultConfigurationFactory defaultConfigurationFactory,
        ResolutionStrategyFactory resolutionStrategyFactory,
        ProblemsInternal problemsService,
        ConfigurationResolver.Factory resolverFactory,
        AttributesSchemaInternal schema
    ) {
        super(Configuration.class, instantiator, Named.Namer.INSTANCE, callbackDecorator);

        this.owner = owner;
        this.defaultConfigurationFactory = defaultConfigurationFactory;
        this.resolutionStrategyFactory = resolutionStrategyFactory;
        this.problemsService = problemsService;

        this.resolver = resolverFactory.create(this, owner, schema);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected Configuration doCreate(String name) {
        // TODO: Deprecate legacy configurations for consumption
        validateNameIsAllowed(name);
        return defaultConfigurationFactory.create(name, false, resolver, resolutionStrategyFactory, ConfigurationRoles.ALL);
    }

    @Override
    protected NamedDomainObjectProvider<Configuration> createDomainObjectProvider(String name, @Nullable Action<? super Configuration> configurationAction) {
        // Called by `register` for registering legacy configurations.
        // We override to set the public type to `LegacyConfiguration`,
        // allowing us to filter for unlocked configurations using `withType`

        assertElementNotPresent(name);
        NamedDomainObjectProvider<Configuration> provider = Cast.uncheckedCast(
            getInstantiator().newInstance(AbstractNamedDomainObjectContainer.NamedDomainObjectCreatingProvider.class, DefaultConfigurationContainer.this, name, LegacyConfiguration.class, configurationAction)
        );
        doAddLater(provider);

        return provider;
    }

    @Override
    public void visitConsumable(Consumer<ConfigurationInternal> visitor) {

        // Visit all configurations which are known to be consumable
        withType(ConsumableConfiguration.class).forEach(configuration ->
            visitor.accept((ConfigurationInternal) configuration)
        );

        // Then, visit any configuration with unknown role, checking if it is consumable
        withType(LegacyConfiguration.class).forEach(configuration -> {
            if (configuration.isCanBeConsumed()) {
                visitor.accept((ConfigurationInternal) configuration);
            }
        });

    }

    @Override
    @Nullable
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

    private RuntimeException failOnAttemptToAdd(String behavior) {
        GradleException ex = new GradleException(behavior);
        ProblemId id = ProblemId.create("method-not-allowed", "Method call not allowed", GradleCoreProblemGroup.configurationUsage());
        throw problemsService.getInternalReporter().throwing(ex, id, spec -> {
            spec.contextualLabel(ex.getMessage());
            spec.severity(Severity.ERROR);
        });
    }

    @Override
    public boolean add(@Nullable Configuration o) {
        throw failOnAttemptToAdd("Adding a configuration directly to the configuration container is not allowed.  Use a factory method instead to create a new configuration in the container.");
    }

    @Override
    public boolean addAll(Collection<? extends Configuration> c) {
        throw failOnAttemptToAdd("Adding a collection of configurations directly to the configuration container is not allowed.  Use a factory method instead to create a new configuration in the container.");
    }

    @Override
    public void addLater(Provider<? extends Configuration> provider) {
        throw failOnAttemptToAdd("Adding a configuration provider directly to the configuration container is not allowed.  Use a factory method instead to create a new configuration in the container.");
    }

    @Override
    public void addAllLater(Provider<? extends Iterable<Configuration>> provider) {
        throw failOnAttemptToAdd("Adding a provider of configurations directly to the configuration container is not allowed.  Use a factory method instead to create a new configuration in the container.");
    }

    @Override
    public ConfigurationInternal detachedConfiguration(Dependency... dependencies) {
        String name = nextDetachedConfigurationName();

        @SuppressWarnings("deprecation")
        ConfigurationRole role = ConfigurationRoles.RESOLVABLE_DEPENDENCY_SCOPE;
        DefaultLegacyConfiguration detachedConfiguration = defaultConfigurationFactory.create(
            name,
            true,
            resolver,
            resolutionStrategyFactory,
            role
        );
        copyAllTo(detachedConfiguration, dependencies);
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
        assertCanMutate("resolvable(String, Action)");
        return registerResolvableConfiguration(name, action);
    }

    @Override
    public Configuration resolvableLocked(String name) {
        assertCanMutate("resolvableLocked(String)");
        return createLockedLegacyConfiguration(name, ConfigurationRoles.RESOLVABLE, Actions.doNothing());
    }

    @Override
    public Configuration resolvableLocked(String name, Action<? super Configuration> action) {
        assertCanMutate("resolvableLocked(String, Action)");
        return createLockedLegacyConfiguration(name, ConfigurationRoles.RESOLVABLE, action);
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
    public Configuration consumableLocked(String name) {
        assertCanMutate("consumableLocked(String)");
        return createLockedLegacyConfiguration(name, ConfigurationRoles.CONSUMABLE, Actions.doNothing());
    }

    @Override
    public Configuration consumableLocked(String name, Action<? super Configuration> action) {
        assertCanMutate("consumableLocked(String, Action)");
        return createLockedLegacyConfiguration(name, ConfigurationRoles.CONSUMABLE, action);
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
    public Configuration dependencyScopeLocked(String name) {
        assertCanMutate("dependencyScopeLocked(String)");
        return createLockedLegacyConfiguration(name, ConfigurationRoles.DEPENDENCY_SCOPE, Actions.doNothing());
    }

    @Override
    public Configuration dependencyScopeLocked(String name, Action<? super Configuration> action) {
        assertCanMutate("dependencyScopeLocked(String, Action)");
        return createLockedLegacyConfiguration(name, ConfigurationRoles.DEPENDENCY_SCOPE, action);
    }

    @Override
    @Deprecated
    public Configuration resolvableDependencyScopeLocked(String name) {
        assertCanMutate("resolvableDependencyScopeLocked(String)");
        return createLockedLegacyConfiguration(name, ConfigurationRoles.RESOLVABLE_DEPENDENCY_SCOPE, Actions.doNothing());
    }

    @Override
    @Deprecated
    public Configuration resolvableDependencyScopeLocked(String name, Action<? super Configuration> action) {
        assertCanMutate("resolvableDependencyScopeLocked(String, Action)");
        return createLockedLegacyConfiguration(name, ConfigurationRoles.RESOLVABLE_DEPENDENCY_SCOPE, action);
    }

    @Override
    public Configuration migratingLocked(String name, ConfigurationRole role) {
        assertCanMutate("migratingLocked(String, ConfigurationRole)");
        return migratingLocked(name, role, Actions.doNothing());
    }

    @Override
    public Configuration migratingLocked(String name, ConfigurationRole role, Action<? super Configuration> action) {
        assertCanMutate("migratingLocked(String, ConfigurationRole, Action)");

        if (ConfigurationRolesForMigration.ALL.contains(role)) {
            return createLockedLegacyConfiguration(name, role, action);
        } else {
            throw new InvalidUserDataException("Unknown migration role: " + role);
        }
    }

    @Override
    public Configuration maybeCreateDependencyScopeLocked(String name, boolean verifyPrexisting) {
        ConfigurationInternal conf = findByName(name);
        if (null != conf) {
            if (verifyPrexisting) {
                throw failOnReservedName(name);
            } else {
                // We should also prevent usage mutation here, but we can't because this would break
                // existing undeprecated behavior.
                // Introduce locking here in Gradle 9.x.
                return getByName(name);
            }
        } else {
            return createLockedLegacyConfiguration(name, ConfigurationRoles.DEPENDENCY_SCOPE, Actions.doNothing());
        }
    }

    private RuntimeException failOnReservedName(String confName) {
        GradleException ex = new GradleException("The configuration " + confName + " was created explicitly. This configuration name is reserved for creation by Gradle.");
        ProblemId id = ProblemId.create("unexpected configuration usage", "Unexpected configuration usage", GradleCoreProblemGroup.configurationUsage());
        throw problemsService.getInternalReporter().throwing(ex, id, spec -> {
            spec.contextualLabel(ex.getMessage());
            spec.severity(Severity.ERROR);
        });
    }

    private NamedDomainObjectProvider<ConsumableConfiguration> registerConsumableConfiguration(String name, Action<? super ConsumableConfiguration> configureAction) {
        return registerConfiguration(name, configureAction, ConsumableConfiguration.class, n ->
            defaultConfigurationFactory.createConsumable(name, resolver, resolutionStrategyFactory)
        );
    }

    private NamedDomainObjectProvider<ResolvableConfiguration> registerResolvableConfiguration(String name, Action<? super ResolvableConfiguration> configureAction) {
        return registerConfiguration(name, configureAction, ResolvableConfiguration.class, n ->
            defaultConfigurationFactory.createResolvable(name, resolver, resolutionStrategyFactory)
        );
    }

    private NamedDomainObjectProvider<DependencyScopeConfiguration> registerDependencyScopeConfiguration(String name, Action<? super DependencyScopeConfiguration> configureAction) {
        return registerConfiguration(name, configureAction, DependencyScopeConfiguration.class, n ->
            defaultConfigurationFactory.createDependencyScope(name, resolver, resolutionStrategyFactory)
        );
    }

    private ConfigurationInternal createLockedLegacyConfiguration(String name, ConfigurationRole role, Action<? super Configuration> configureAction) {
        assertElementNotPresent(name);
        validateNameIsAllowed(name);
        ConfigurationInternal configuration = defaultConfigurationFactory.create(name, false, resolver, resolutionStrategyFactory, role);
        super.add(configuration);
        configureAction.execute(configuration);
        configuration.preventUsageMutation();
        configuration.setVisible(false);
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

    private void validateNameIsAllowed(String name) {
        if (RESERVED_NAMES_FOR_DETACHED_CONFS.matcher(name).matches()) {
            GradleException ex = new GradleException(String.format("Creating a configuration with a name that starts with 'detachedConfiguration' is not allowed.  Use a different name for the configuration '%s'", name));
            ProblemId id = ProblemId.create("name-not-allowed", "Configuration name not allowed", GradleCoreProblemGroup.configurationUsage());
            throw problemsService.getInternalReporter().throwing(ex, id, spec -> {
                spec.contextualLabel(ex.getMessage());
                spec.severity(Severity.ERROR);
            });
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
