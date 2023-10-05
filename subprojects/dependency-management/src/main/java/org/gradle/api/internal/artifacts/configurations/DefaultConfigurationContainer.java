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
import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
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
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.DefaultRootComponentMetadataBuilder;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.RootComponentMetadataBuilder;
import org.gradle.internal.Actions;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.deprecation.DeprecationMessageBuilder;
import org.gradle.internal.exceptions.ResolutionProvider;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.GradleVersion;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
    private final RootComponentMetadataBuilder rootComponentMetadataBuilder;
    private final DefaultConfigurationFactory defaultConfigurationFactory;
    @Nullable private String maybeCreateContextDesc;

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
    public Optional<String> getMaybeCreateContext() {
        return Optional.ofNullable(maybeCreateContextDesc);
    }

    @Override
    public void recordMaybeCreateContext(@Nullable String contextDescription) {
        maybeCreateContextDesc = contextDescription;
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
    public Configuration resolvableUnlocked(String name) {
        assertMutable("resolvableUnlocked(String)");
        return createUnlockedConfiguration(name, ConfigurationRoles.RESOLVABLE, Actions.doNothing());
    }

    @Override
    public Configuration resolvableUnlocked(String name, Action<? super Configuration> action) {
        assertMutable("resolvableUnlocked(String, Action)");
        return createUnlockedConfiguration(name, ConfigurationRoles.RESOLVABLE, action);
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
    public Configuration consumableUnlocked(String name) {
        assertMutable("consumableUnlocked(String)");
        return createUnlockedConfiguration(name, ConfigurationRoles.CONSUMABLE, Actions.doNothing());
    }

    @Override
    public Configuration consumableUnlocked(String name, Action<? super Configuration> action) {
        assertMutable("consumableUnlocked(String, Action)");
        return createUnlockedConfiguration(name, ConfigurationRoles.CONSUMABLE, action);
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
    public Configuration dependencyScopeUnlocked(String name) {
        assertMutable("dependencyScopeUnlocked(String)");
        return createUnlockedConfiguration(name, ConfigurationRoles.DEPENDENCY_SCOPE, Actions.doNothing());
    }

    @Override
    public Configuration dependencyScopeUnlocked(String name, Action<? super Configuration> action) {
        assertMutable("dependencyScopeUnlocked(String, Action)");
        return createUnlockedConfiguration(name, ConfigurationRoles.DEPENDENCY_SCOPE, action);
    }

    @Override
    @Deprecated
    public Configuration resolvableDependencyScopeUnlocked(String name) {
        assertMutable("resolvableDependencyScopeUnlocked(String)");
        return createUnlockedConfiguration(name, ConfigurationRoles.RESOLVABLE_DEPENDENCY_SCOPE, Actions.doNothing());
    }

    @Override
    @Deprecated
    public Configuration resolvableDependencyScopeUnlocked(String name, Action<? super Configuration> action) {
        assertMutable("resolvableDependencyScopeUnlocked(String, Action)");
        return createUnlockedConfiguration(name, ConfigurationRoles.RESOLVABLE_DEPENDENCY_SCOPE, action);
    }

    @Override
    public Configuration migratingUnlocked(String name, ConfigurationRole role) {
        assertMutable("migratingUnlocked(String, ConfigurationRole)");
        return migratingUnlocked(name, role, Actions.doNothing());
    }

    @Override
    public Configuration migratingUnlocked(String name, ConfigurationRole role, Action<? super Configuration> action) {
        assertMutable("migratingUnlocked(String, ConfigurationRole, Action)");

        if (!ConfigurationRolesForMigration.ALL.contains(role)) {
            throw new InvalidUserDataException("Unknown migration role: " + role);
        }

        return createUnlockedConfiguration(name, role, action);
    }

    @Override
    public Configuration maybeCreateResolvableUnlocked(String name) {
        if (!hasWithName(name)) {
            return resolvableUnlocked(name);
        }

        emitConfigurationExistsDeprecation(name);
        validateExistingUsageIsConsistent(name, ConfigurationRoles.RESOLVABLE);
        return getByName(name);
    }

    @Override
    public Configuration maybeCreateConsumableUnlocked(String name) {
        if (!hasWithName(name)) {
            return consumableUnlocked(name);
        }

        emitConfigurationExistsDeprecation(name);
        validateExistingUsageIsConsistent(name, ConfigurationRoles.CONSUMABLE);
        return getByName(name);
    }

    @Override
    public Configuration maybeCreateDependencyScopeUnlocked(String name) {
        return maybeCreateDependencyScopeUnlocked(name, true);
    }

    @Override
    public Configuration maybeCreateDependencyScopeUnlocked(String name, boolean warnOnDuplicate) {
        if (!hasWithName(name)) {
            return dependencyScopeUnlocked(name);
        }

        if (warnOnDuplicate) {
            emitConfigurationExistsDeprecation(name);
            validateExistingUsageIsConsistent(name, ConfigurationRoles.DEPENDENCY_SCOPE);
        }
        return getByName(name);
    }

    @Override
    public Configuration maybeCreateMigratingUnlocked(String name, ConfigurationRole role) {
        if (!hasWithName(name)) {
            return migratingUnlocked(name, role);
        }

        emitConfigurationExistsDeprecation(name);
        validateExistingUsageIsConsistent(name, role);
        return getByName(name);
    }

    @Override
    @Deprecated
    public Configuration maybeCreateResolvableDependencyScopeUnlocked(String name) {
        if (!hasWithName(name)) {
            return resolvableDependencyScopeUnlocked(name);
        }

        emitConfigurationExistsDeprecation(name);
        validateExistingUsageIsConsistent(name, ConfigurationRoles.RESOLVABLE_DEPENDENCY_SCOPE);
        return getByName(name);
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
        assert GradleVersion.current().getBaseVersion().compareTo(GradleVersion.version("9.0")) < 0 || role == ConfigurationRoles.LEGACY;

        // TODO: Deprecate changing roles of unlocked non-legacy configurations.

        assertCanAdd(name);
        validateNameIsAllowed(name);
        Configuration configuration = defaultConfigurationFactory.create(name, this, resolutionStrategyFactory, rootComponentMetadataBuilder, role);
        add(configuration);
        configureAction.execute(configuration);
        return configuration;
    }

    private <T extends Configuration> NamedDomainObjectProvider<T> registerConfiguration(String name, Action<? super T> configureAction, Class<T> publicType, Function<String, T> factory) {
        assertCanAdd(name);
        validateNameIsAllowed(name);

        NamedDomainObjectProvider<T> configuration = Cast.uncheckedCast(
            getInstantiator().newInstance(NamedDomainObjectCreatingProvider.class, this, name, publicType, configureAction, factory));
        addLater(configuration);
        return configuration;
    }

    private static void emitConfigurationExistsDeprecation(String name) {
        DeprecationLogger.deprecateBehaviour("The configuration " + name + " was created explicitly. This configuration name is reserved for creation by Gradle.")
            .withAdvice(String.format("Do not create a configuration with the name %s.", name))
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

    /**
     * Validates and ensures that the allowed usage of an existing configuration is consistent with the given expected usage.
     *
     * This method will emit a detailed deprecation method with suggestions if the usage is inconsistent.  It will then attempt to mutate
     * the usage to match the expectation.  If the usage cannot be mutated, it will throw an exception.
     *
     * Does <strong>NOT</strong> check anything to do with deprecated usage.
     *
     * @param confName the name of the configuration
     * @param expectedUsage the expectedUsage defining the usage the configuration should allow
     */
    private void validateExistingUsageIsConsistent(String confName, ConfigurationRole expectedUsage) {
        ConfigurationInternal conf = getByName(confName);
        if (expectedUsage.isUsageConsistentWithRole(conf)) {
            return;
        }

        String currentUsageDesc = UsageDescriber.describeCurrentUsage(conf);
        String expectedUsageDesc = UsageDescriber.describeRole(expectedUsage);

        boolean hasContext = getMaybeCreateContext().isPresent();
        boolean hasSourceSetContext = hasContext && getMaybeCreateContext().get().contains("sourceSet");

        String msgDiscovery;
        if (hasContext) {
            msgDiscovery = String.format("When creating configurations during %s, Gradle found that configuration %s already exists with permitted usage(s):\n" +
                "%s\n", getMaybeCreateContext().get(), confName, currentUsageDesc);
        } else {
            msgDiscovery = String.format("Configuration %s already exists with permitted usage(s):\n" +
                "%s\n", confName, currentUsageDesc);
        }

        String msgExpectation = String.format("Yet Gradle expected to create it with the usage(s):\n" +
            "%s\n" +
            "Gradle will mutate the usage of configuration %s to match the expected usage. This may cause unexpected behavior. Creating configurations with reserved names", expectedUsageDesc, confName);
        String basicNameAdvice = String.format("Do not create a configuration with the name %s.", confName);
        String sourceSetAdvice = "Create sourceSets prior to creating or accessing the configurations associated with them.";

        DeprecationMessageBuilder<?> builder = DeprecationLogger.deprecate(msgDiscovery + msgExpectation);
        if (hasSourceSetContext) {
            builder.withAdvice(sourceSetAdvice)
                .willBecomeAnErrorInGradle9()
                .withUserManual("building_java_projects", "sec:implicit_sourceset_configurations")
                .nagUser();
        } else {
            builder.withAdvice(basicNameAdvice)
                .willBecomeAnErrorInGradle9()
                .withUserManual("authoring_maintainable_build_scripts", "sec:dont_anticipate_configuration_creation")
                .nagUser();
        }

        if (conf.usageCanBeMutated()) {
            conf.setAllowedUsageFromRole(expectedUsage);
        } else {
            List<String> resolutions = Lists.newArrayList(basicNameAdvice);
            if (hasSourceSetContext) {
                resolutions.add(sourceSetAdvice);
            }
            throw new UnmodifiableConfigurationException(confName, resolutions);
        }
    }

    /**
     * An exception to be thrown when Gradle cannot mutate the usage of a configuration.
     */
    public static class UnmodifiableConfigurationException extends GradleException implements ResolutionProvider {
        private final List<String> resolutions;

        public UnmodifiableConfigurationException(String configurationName, List<String> resolutions) {
            super(String.format("Gradle cannot mutate the usage of configuration '%s' because it is locked.", configurationName));
            this.resolutions = resolutions;
        }

        @Override
        public List<String> getResolutions() {
            return Collections.unmodifiableList(resolutions);
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
}
