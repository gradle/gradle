/*
 * Copyright 2011 the original author or authors.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import groovy.lang.Closure;
import org.apache.commons.lang.WordUtils;
import org.gradle.api.Action;
import org.gradle.api.Describable;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.ConfigurablePublishArtifact;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationPublications;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.DependencyConstraintSet;
import org.gradle.api.artifacts.DependencyResolutionListener;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.PublishArtifactSet;
import org.gradle.api.artifacts.ResolutionStrategy;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.CompositeDomainObjectSet;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.ConfigurationResolver;
import org.gradle.api.internal.artifacts.DefaultDependencyConstraintSet;
import org.gradle.api.internal.artifacts.DefaultDependencySet;
import org.gradle.api.internal.artifacts.DefaultExcludeRule;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.DefaultPublishArtifactSet;
import org.gradle.api.internal.artifacts.ExcludeRuleNotationConverter;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.ResolveContext;
import org.gradle.api.internal.artifacts.ResolveExceptionContextualizer;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory;
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyConstraint;
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint;
import org.gradle.api.internal.artifacts.dependencies.DependencyConstraintInternal;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingState;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.RootComponentMetadataBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSelectionSpec;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.VisitedGraphResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult.ResolvedProjectConfiguration;
import org.gradle.api.internal.artifacts.result.DefaultResolutionResult;
import org.gradle.api.internal.artifacts.result.MinimalResolutionResult;
import org.gradle.api.internal.artifacts.transform.DefaultTransformUpstreamDependenciesResolverFactory;
import org.gradle.api.internal.artifacts.transform.TransformUpstreamDependenciesResolverFactory;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributeContainerWithErrorMessage;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.api.internal.initialization.ResettableConfiguration;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.Cast;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.ImmutableActionSet;
import org.gradle.internal.code.UserCodeApplicationContext;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.external.model.ProjectDerivedCapability;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.LocalComponentDependencyMetadata;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.deprecation.DocumentedFailure;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.lazy.Lazy;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.model.CalculatedModelValue;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.work.WorkerThreadRegistry;
import org.gradle.operations.dependencies.configurations.ConfigurationIdentity;
import org.gradle.util.Path;
import org.gradle.util.internal.CollectionUtils;
import org.gradle.util.internal.ConfigureUtil;
import org.gradle.util.internal.WrapUtil;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gradle.api.internal.artifacts.configurations.ConfigurationInternal.InternalState.ARTIFACTS_RESOLVED;
import static org.gradle.api.internal.artifacts.configurations.ConfigurationInternal.InternalState.BUILD_DEPENDENCIES_RESOLVED;
import static org.gradle.api.internal.artifacts.configurations.ConfigurationInternal.InternalState.GRAPH_RESOLVED;
import static org.gradle.api.internal.artifacts.configurations.ConfigurationInternal.InternalState.UNRESOLVED;
import static org.gradle.util.internal.ConfigureUtil.configure;

/**
 * The default {@link Configuration} implementation.
 * <p>
 * After initialization, when the allowed usage is changed then role-related deprecation warnings will be emitted, except for the special cases
 * noted in {@link #isSpecialCaseOfChangingUsage(String, boolean)}}.  Initialization is complete when the {@link #roleAtCreation} field is set.
 */
@SuppressWarnings("rawtypes")
public abstract class DefaultConfiguration extends AbstractFileCollection implements ConfigurationInternal, MutationValidator, ResettableConfiguration {
    private final ConfigurationResolver resolver;
    private final DependencyMetaDataProvider metaDataProvider;
    private final ComponentIdentifierFactory componentIdentifierFactory;
    private final DependencyLockingProvider dependencyLockingProvider;
    private final DefaultDependencySet dependencies;
    private final DefaultDependencyConstraintSet dependencyConstraints;
    private final DefaultDomainObjectSet<Dependency> ownDependencies;
    private final DefaultDomainObjectSet<DependencyConstraint> ownDependencyConstraints;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;
    private final ProjectStateRegistry projectStateRegistry;
    private CompositeDomainObjectSet<Dependency> inheritedDependencies;
    private CompositeDomainObjectSet<DependencyConstraint> inheritedDependencyConstraints;
    private DefaultDependencySet allDependencies;
    private DefaultDependencyConstraintSet allDependencyConstraints;
    private ImmutableActionSet<DependencySet> defaultDependencyActions = ImmutableActionSet.empty();
    private ImmutableActionSet<DependencySet> withDependencyActions = ImmutableActionSet.empty();
    private final DefaultPublishArtifactSet artifacts;
    private final DefaultDomainObjectSet<PublishArtifact> ownArtifacts;
    private CompositeDomainObjectSet<PublishArtifact> inheritedArtifacts;
    private DefaultPublishArtifactSet allArtifacts;
    private final ConfigurationResolvableDependencies resolvableDependencies;
    private ListenerBroadcast<DependencyResolutionListener> dependencyResolutionListeners;
    private final ProjectDependencyObservedListener dependencyObservedBroadcast;
    private final BuildOperationExecutor buildOperationExecutor;
    private final Instantiator instantiator;
    private Factory<ResolutionStrategyInternal> resolutionStrategyFactory;
    private ResolutionStrategyInternal resolutionStrategy;
    private final FileCollectionFactory fileCollectionFactory;
    private final ResolveExceptionContextualizer exceptionContextualizer;

    private final Set<MutationValidator> childMutationValidators = Sets.newHashSet();
    private final MutationValidator parentMutationValidator = DefaultConfiguration.this::validateParentMutation;
    private final RootComponentMetadataBuilder rootComponentMetadataBuilder;
    private final ConfigurationsProvider configurationsProvider;

    private final Path identityPath;
    private final Path projectPath;

    // These fields are not covered by mutation lock
    private final String name;
    private final DefaultConfigurationPublications outgoing;

    private boolean visible = true;
    private boolean transitive = true;
    private Set<Configuration> extendsFrom = new LinkedHashSet<>();
    private String description;
    private final Set<Object> excludeRules = new LinkedHashSet<>();
    private Set<ExcludeRule> parsedExcludeRules;

    private final Object observationLock = new Object();
    private volatile InternalState observedState = UNRESOLVED;
    private boolean insideBeforeResolve;

    private boolean dependenciesModified;
    private boolean canBeConsumed;
    private boolean canBeResolved;
    private boolean canBeDeclaredAgainst;
    private final boolean consumptionDeprecated;
    private final boolean resolutionDeprecated;
    private final boolean declarationDeprecated;
    private boolean usageCanBeMutated = true;
    private final ConfigurationRole roleAtCreation;

    private boolean canBeMutated = true;
    private AttributeContainerInternal configurationAttributes;
    private final DomainObjectContext domainObjectContext;
    private final ImmutableAttributesFactory attributesFactory;
    private final ResolutionBackedFileCollection intrinsicFiles;

    private final DisplayName displayName;
    private final UserCodeApplicationContext userCodeApplicationContext;
    private final WorkerThreadRegistry workerThreadRegistry;
    private final DomainObjectCollectionFactory domainObjectCollectionFactory;
    private final Lazy<List<? extends DependencyMetadata>> syntheticDependencies = Lazy.unsafe().of(this::generateSyntheticDependencies);

    private final AtomicInteger copyCount = new AtomicInteger();

    private List<String> declarationAlternatives = ImmutableList.of();
    private List<String> resolutionAlternatives = ImmutableList.of();

    private final CalculatedModelValue<ResolveState> currentResolveState;

    private ConfigurationInternal consistentResolutionSource;
    private String consistentResolutionReason;
    private TransformUpstreamDependenciesResolverFactory dependenciesResolverFactory;
    private final DefaultConfigurationFactory defaultConfigurationFactory;

    /**
     * To create an instance, use {@link DefaultConfigurationFactory#create}.
     */
    public DefaultConfiguration(
        DomainObjectContext domainObjectContext,
        String name,
        ConfigurationsProvider configurationsProvider,
        ConfigurationResolver resolver,
        ListenerBroadcast<DependencyResolutionListener> dependencyResolutionListeners,
        ProjectDependencyObservedListener dependencyObservedBroadcast,
        DependencyMetaDataProvider metaDataProvider,
        ComponentIdentifierFactory componentIdentifierFactory,
        DependencyLockingProvider dependencyLockingProvider,
        Factory<ResolutionStrategyInternal> resolutionStrategyFactory,
        FileCollectionFactory fileCollectionFactory,
        BuildOperationExecutor buildOperationExecutor,
        Instantiator instantiator,
        NotationParser<Object, ConfigurablePublishArtifact> artifactNotationParser,
        NotationParser<Object, Capability> capabilityNotationParser,
        ImmutableAttributesFactory attributesFactory,
        RootComponentMetadataBuilder rootComponentMetadataBuilder,
        ResolveExceptionContextualizer exceptionContextualizer,
        UserCodeApplicationContext userCodeApplicationContext,
        ProjectStateRegistry projectStateRegistry,
        WorkerThreadRegistry workerThreadRegistry,
        DomainObjectCollectionFactory domainObjectCollectionFactory,
        CalculatedValueContainerFactory calculatedValueContainerFactory,
        DefaultConfigurationFactory defaultConfigurationFactory,
        TaskDependencyFactory taskDependencyFactory,
        ConfigurationRole roleAtCreation,
        boolean lockUsage
    ) {
        super(taskDependencyFactory);
        this.userCodeApplicationContext = userCodeApplicationContext;
        this.projectStateRegistry = projectStateRegistry;
        this.workerThreadRegistry = workerThreadRegistry;
        this.domainObjectCollectionFactory = domainObjectCollectionFactory;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
        this.identityPath = domainObjectContext.identityPath(name);
        this.projectPath = domainObjectContext.projectPath(name);
        this.name = name;
        this.configurationsProvider = configurationsProvider;
        this.resolver = resolver;
        this.metaDataProvider = metaDataProvider;
        this.componentIdentifierFactory = componentIdentifierFactory;
        this.dependencyLockingProvider = dependencyLockingProvider;
        this.resolutionStrategyFactory = resolutionStrategyFactory;
        this.fileCollectionFactory = fileCollectionFactory;
        this.dependencyResolutionListeners = dependencyResolutionListeners;
        this.dependencyObservedBroadcast = dependencyObservedBroadcast;
        this.buildOperationExecutor = buildOperationExecutor;
        this.instantiator = instantiator;
        this.attributesFactory = attributesFactory;
        this.configurationAttributes = attributesFactory.mutable();
        this.domainObjectContext = domainObjectContext;
        this.intrinsicFiles = fileCollectionFromSpec(Specs.satisfyAll());
        this.exceptionContextualizer = exceptionContextualizer;
        this.resolvableDependencies = instantiator.newInstance(ConfigurationResolvableDependencies.class, this);

        displayName = Describables.memoize(new ConfigurationDescription(identityPath));

        this.ownDependencies = (DefaultDomainObjectSet<Dependency>) domainObjectCollectionFactory.newDomainObjectSet(Dependency.class);
        this.ownDependencies.beforeCollectionChanges(validateMutationType(this, MutationType.DEPENDENCIES));
        this.ownDependencyConstraints = (DefaultDomainObjectSet<DependencyConstraint>) domainObjectCollectionFactory.newDomainObjectSet(DependencyConstraint.class);
        this.ownDependencyConstraints.beforeCollectionChanges(validateMutationType(this, MutationType.DEPENDENCIES));

        this.dependencies = new DefaultDependencySet(Describables.of(displayName, "dependencies"), this, ownDependencies);
        this.dependencyConstraints = new DefaultDependencyConstraintSet(Describables.of(displayName, "dependency constraints"), this, ownDependencyConstraints);

        this.ownArtifacts = (DefaultDomainObjectSet<PublishArtifact>) domainObjectCollectionFactory.newDomainObjectSet(PublishArtifact.class);
        this.ownArtifacts.beforeCollectionChanges(validateMutationType(this, MutationType.ARTIFACTS));

        this.artifacts = new DefaultPublishArtifactSet(Describables.of(displayName, "artifacts"), ownArtifacts, fileCollectionFactory, taskDependencyFactory);

        this.outgoing = instantiator.newInstance(DefaultConfigurationPublications.class, displayName, artifacts, new AllArtifactsProvider(), configurationAttributes, instantiator, artifactNotationParser, capabilityNotationParser, fileCollectionFactory, attributesFactory, domainObjectCollectionFactory, taskDependencyFactory);
        this.rootComponentMetadataBuilder = rootComponentMetadataBuilder;
        this.currentResolveState = domainObjectContext.getModel().newCalculatedValue(ResolveState.NOT_RESOLVED);
        this.defaultConfigurationFactory = defaultConfigurationFactory;

        this.canBeConsumed = roleAtCreation.isConsumable();
        this.canBeResolved = roleAtCreation.isResolvable();
        this.canBeDeclaredAgainst = roleAtCreation.isDeclarable();
        this.consumptionDeprecated = roleAtCreation.isConsumptionDeprecated();
        this.resolutionDeprecated = roleAtCreation.isResolutionDeprecated();
        this.declarationDeprecated = roleAtCreation.isDeclarationAgainstDeprecated();

        if (lockUsage) {
            preventUsageMutation();
        }

        // Until the role at creation is set, changing usage won't trigger warnings
        this.roleAtCreation = roleAtCreation;
    }

    private static Action<Void> validateMutationType(final MutationValidator mutationValidator, final MutationType type) {
        return arg -> mutationValidator.validateMutation(type);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public State getState() {
        ResolveState currentState = currentResolveState.get();
        InternalState resolvedState = currentState.state;
        if (resolvedState == ARTIFACTS_RESOLVED || resolvedState == GRAPH_RESOLVED) {
            if (currentState.hasError()) {
                return State.RESOLVED_WITH_FAILURES;
            } else {
                return State.RESOLVED;
            }
        } else {
            return State.UNRESOLVED;
        }
    }

    /**
     * Get the current resolved state of this configuration.
     * <p>
     * Usage: This method should only be called on resolvable configurations and should throw an exception if
     * called on a configuration that does not permit this usage.
     *
     * @return the current resolved state of this configuration
     */
    @VisibleForTesting
    public InternalState getResolvedState() {
        warnOnInvalidInternalAPIUsage("getResolvedState()", ProperMethodUsage.RESOLVABLE);
        return currentResolveState.get().state;
    }

    @Override
    public Module getModule() {
        return metaDataProvider.getModule();
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public Configuration setVisible(boolean visible) {
        validateMutation(MutationType.DEPENDENCIES);
        this.visible = visible;
        return this;
    }

    @Override
    public Set<Configuration> getExtendsFrom() {
        return Collections.unmodifiableSet(extendsFrom);
    }

    @Override
    public Configuration setExtendsFrom(Iterable<Configuration> extendsFrom) {
        validateMutation(MutationType.HIERARCHY);
        for (Configuration configuration : this.extendsFrom) {
            if (inheritedArtifacts != null) {
                inheritedArtifacts.removeCollection(configuration.getAllArtifacts());
            }
            if (inheritedDependencies != null) {
                inheritedDependencies.removeCollection(configuration.getAllDependencies());
            }
            if (inheritedDependencyConstraints != null) {
                inheritedDependencyConstraints.removeCollection(configuration.getAllDependencyConstraints());
            }
            ((ConfigurationInternal) configuration).removeMutationValidator(parentMutationValidator);
        }
        this.extendsFrom = new LinkedHashSet<>();
        for (Configuration configuration : extendsFrom) {
            extendsFrom(configuration);
        }
        return this;
    }

    @Override
    public Configuration extendsFrom(Configuration... extendsFrom) {
        validateMutation(MutationType.HIERARCHY);
        for (Configuration configuration : extendsFrom) {
            if (configuration.getHierarchy().contains(this)) {
                throw new InvalidUserDataException(String.format(
                    "Cyclic extendsFrom from %s and %s is not allowed. See existing hierarchy: %s", this,
                    configuration, configuration.getHierarchy()));
            }
            if (this.extendsFrom.add(configuration)) {
                if (inheritedArtifacts != null) {
                    inheritedArtifacts.addCollection(configuration.getAllArtifacts());
                }
                if (inheritedDependencies != null) {
                    inheritedDependencies.addCollection(configuration.getAllDependencies());
                }
                if (inheritedDependencyConstraints != null) {
                    inheritedDependencyConstraints.addCollection(configuration.getAllDependencyConstraints());
                }
                ((ConfigurationInternal) configuration).addMutationValidator(parentMutationValidator);
            }
        }
        return this;
    }

    @Override
    public boolean isTransitive() {
        return transitive;
    }

    @Override
    public Configuration setTransitive(boolean transitive) {
        validateMutation(MutationType.DEPENDENCIES);
        this.transitive = transitive;
        return this;
    }

    @Override
    @Nullable
    public String getDescription() {
        return description;
    }

    @Override
    public Configuration setDescription(@Nullable String description) {
        this.description = description;
        return this;
    }

    @Override
    public Set<Configuration> getHierarchy() {
        if (extendsFrom.isEmpty()) {
            return Collections.singleton(this);
        }
        Set<Configuration> result = WrapUtil.toLinkedSet(this);
        collectSuperConfigs(this, result);
        return result;
    }

    private void collectSuperConfigs(Configuration configuration, Set<Configuration> result) {
        for (Configuration superConfig : configuration.getExtendsFrom()) {
            // The result is an ordered set - so seeing the same value a second time pushes further down
            result.remove(superConfig);
            result.add(superConfig);
            collectSuperConfigs(superConfig, result);
        }
    }

    @Override
    public Configuration defaultDependencies(final Action<? super DependencySet> action) {
        warnOnDeprecatedUsage("defaultDependencies(Action)", ProperMethodUsage.DECLARABLE_AGAINST);
        validateMutation(MutationType.DEPENDENCIES);
        defaultDependencyActions = defaultDependencyActions.add(dependencies -> {
            if (dependencies.isEmpty()) {
                action.execute(dependencies);
            }
        });
        return this;
    }

    @Override
    public Configuration withDependencies(final Action<? super DependencySet> action) {
        validateMutation(MutationType.DEPENDENCIES);
        withDependencyActions = withDependencyActions.add(action);
        return this;
    }

    @Override
    public void runDependencyActions() {
        defaultDependencyActions.execute(dependencies);
        withDependencyActions.execute(dependencies);

        // Discard actions after execution
        defaultDependencyActions = ImmutableActionSet.empty();
        withDependencyActions = ImmutableActionSet.empty();

        for (Configuration superConfig : extendsFrom) {
            ((ConfigurationInternal) superConfig).runDependencyActions();
        }
    }

    @Deprecated
    @Override
    public Set<Configuration> getAll() {
        DeprecationLogger.deprecateAction("Calling the Configuration.getAll() method")
                .withAdvice("Use the configurations container to access the set of configurations instead.")
                .willBeRemovedInGradle9()
                .withUpgradeGuideSection(8, "deprecated_configuration_get_all")
                .nagUser();

        return ImmutableSet.copyOf(configurationsProvider.getAll());
    }

    @Override
    public Set<File> resolve() {
        warnOnDeprecatedUsage("resolve()", ProperMethodUsage.RESOLVABLE);
        return getFiles();
    }

    @Override
    public Iterator<File> iterator() {
        return intrinsicFiles.iterator();
    }

    @Override
    protected void visitContents(FileCollectionStructureVisitor visitor) {
        intrinsicFiles.visitStructure(visitor);
    }

    @Override
    protected void appendContents(TreeFormatter formatter) {
        formatter.node("configuration: " + identityPath);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote Usage: This method should only be called on resolvable configurations and should throw an exception if
     * called on a configuration that does not permit this usage.
     */
    @Override
    public boolean contains(File file) {
        warnOnInvalidInternalAPIUsage("contains(File)", ProperMethodUsage.RESOLVABLE);
        return intrinsicFiles.contains(file);
    }

    @Override
    public boolean isEmpty() {
        return intrinsicFiles.isEmpty();
    }

    @Override
    public Set<File> files(Dependency... dependencies) {
        return fileCollection(dependencies).getFiles();
    }

    @Override
    public Set<File> files(Closure dependencySpecClosure) {
        return fileCollection(dependencySpecClosure).getFiles();
    }

    @Override
    public Set<File> files(Spec<? super Dependency> dependencySpec) {
        return fileCollection(dependencySpec).getFiles();
    }

    @Override
    public FileCollection fileCollection(Spec<? super Dependency> dependencySpec) {
        assertIsResolvable();
        // After asserting, we are definitely allowed, but might be deprecated, so check to warn now
        warnOnDeprecatedUsage("fileCollection(Spec)", ProperMethodUsage.RESOLVABLE);
        return fileCollectionFromSpec(dependencySpec);
    }

    private ResolutionBackedFileCollection fileCollectionFromSpec(Spec<? super Dependency> dependencySpec) {
        return new ResolutionBackedFileCollection(
            new SelectedArtifactsProvider(dependencySpec, configurationAttributes, Specs.satisfyAll(), false, false, new VisitedArtifactsSetProvider()),
            false,
            new DefaultResolutionHost(),
            taskDependencyFactory
        );
    }

    @Override
    public FileCollection fileCollection(Closure dependencySpecClosure) {
        warnOnDeprecatedUsage("fileCollection(Closure)", ProperMethodUsage.RESOLVABLE);
        return fileCollection(Specs.convertClosureToSpec(dependencySpecClosure));
    }

    @Override
    public FileCollection fileCollection(Dependency... dependencies) {
        warnOnDeprecatedUsage("fileCollection(Dependency...)", ProperMethodUsage.RESOLVABLE);
        Set<Dependency> deps = WrapUtil.toLinkedSet(dependencies);
        return fileCollection(deps::contains);
    }

    @Override
    public void markAsObserved(InternalState requestedState) {
        markThisObserved(requestedState);
        markParentsObserved(requestedState);
    }

    private void markThisObserved(InternalState requestedState) {
        synchronized (observationLock) {
            if (observedState.compareTo(requestedState) < 0) {
                observedState = requestedState;
            }
        }
    }

    @VisibleForTesting
    protected InternalState getObservedState() {
        return observedState;
    }

    private void markParentsObserved(InternalState requestedState) {
        for (Configuration configuration : extendsFrom) {
            ((ConfigurationInternal) configuration).markAsObserved(requestedState);
        }
    }

    @Override
    public ResolvedConfiguration getResolvedConfiguration() {
        warnOnDeprecatedUsage("getResolvedConfiguration()", ProperMethodUsage.RESOLVABLE);
        return resolveToStateOrLater(ARTIFACTS_RESOLVED).getResolvedConfiguration();
    }

    private ResolveState resolveToStateOrLater(final InternalState requestedState) {
        assertIsResolvable();
        maybeEmitResolutionDeprecation();

        ResolveState currentState = currentResolveState.get();
        if (currentState.state.compareTo(requestedState) >= 0) {
            return currentState;
        }

        if (!domainObjectContext.getModel().hasMutableState()) {
            if (!workerThreadRegistry.isWorkerThread()) {
                // Error if we are executing in a user-managed thread.
                throw new IllegalStateException("The configuration " + identityPath.toString() + " was resolved from a thread not managed by Gradle.");
            } else {
                DeprecationLogger.deprecateBehaviour("Resolution of the configuration " + identityPath.toString() + " was attempted from a context different than the project context. Have a look at the documentation to understand why this is a problem and how it can be resolved.")
                        .willBecomeAnErrorInGradle9()
                        .withUserManual("viewing_debugging_dependencies", "sub:resolving-unsafe-configuration-resolution-errors")
                        .nagUser();
                return domainObjectContext.getModel().fromMutableState(p -> resolveExclusively(requestedState));
            }
        }
        return resolveExclusively(requestedState);
    }

    private ResolveState resolveExclusively(InternalState requestedState) {
        return currentResolveState.update(initial -> {
            ResolveState current = initial;
            if (requestedState == GRAPH_RESOLVED || requestedState == ARTIFACTS_RESOLVED) {
                current = resolveGraphIfRequired(requestedState, current);
            }
            if (requestedState == ARTIFACTS_RESOLVED) {
                current = resolveArtifactsIfRequired(current);
            }
            return current;
        });
    }

    /**
     * Must be called from {@link #resolveExclusively(InternalState)} only.
     */
    private ResolveState resolveGraphIfRequired(final InternalState requestedState, ResolveState currentState) {
        if (currentState.state == ARTIFACTS_RESOLVED || currentState.state == GRAPH_RESOLVED) {
            if (dependenciesModified) {
                throw new InvalidUserDataException(String.format("Attempted to resolve %s that has been resolved previously.", getDisplayName()));
            }
            return currentState;
        }

        return buildOperationExecutor.call(new CallableBuildOperation<ResolveState>() {
            @Override
            public ResolveState call(BuildOperationContext context) {
                runDependencyActions();
                preventFromFurtherMutation();

                ResolvableDependenciesInternal incoming = (ResolvableDependenciesInternal) getIncoming();
                performPreResolveActions(incoming);
                ResolverResults results = resolver.resolveGraph(DefaultConfiguration.this);
                dependenciesModified = false;

                ResolveState newState = new GraphResolved(results);

                // Make the new state visible in case a dependency resolution listener queries the result, which requires the new state
                currentResolveState.set(newState);

                // Mark all affected configurations as observed
                markParentsObserved(requestedState);
                markReferencedProjectConfigurationsObserved(requestedState, results);

                // TODO: Currently afterResolve runs if there is not an non-unresolved-dependency failure
                //       We should either _always_ run afterResolve, or only run it if _no_ failure occurred
                if (!newState.getCachedResolverResults().getVisitedGraph().getResolutionFailure().isPresent()) {
                    dependencyResolutionListeners.getSource().afterResolve(incoming);

                    // Use the current state, which may have changed if the listener queried the result
                    newState = currentResolveState.get();
                }

                // Discard State
                dependencyResolutionListeners.removeAll();
                if (resolutionStrategy != null) {
                    resolutionStrategy.discardStateRequiredForGraphResolution();
                }

                captureBuildOperationResult(context, results);
                return newState;
            }

            private void captureBuildOperationResult(BuildOperationContext context, ResolverResults results) {
                results.getVisitedGraph().getResolutionFailure().ifPresent(context::failed);
                // When dependency resolution has failed, we don't want the build operation listeners to fail as well
                // because:
                // 1. the `failed` method will have been called with the user facing error
                // 2. such an error may still lead to a valid dependency graph
                ResolutionResult resolutionResult = new DefaultResolutionResult(results.getVisitedGraph().getResolutionResult());
                context.setResult(ResolveConfigurationResolutionBuildOperationResult.create(resolutionResult, attributesFactory));
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                String displayName = "Resolve dependencies of " + identityPath;
                Path projectPath = domainObjectContext.getProjectPath();
                String projectPathString = null;
                if (!domainObjectContext.isScript()) {
                    if (projectPath != null) {
                        projectPathString = projectPath.getPath();
                    }
                }
                return BuildOperationDescriptor.displayName(displayName)
                    .progressDisplayName(displayName)
                    .details(new ResolveConfigurationResolutionBuildOperationDetails(
                        getName(),
                        domainObjectContext.isScript(),
                        getDescription(),
                        domainObjectContext.getBuildPath().getPath(),
                        projectPathString,
                        isVisible(),
                        isTransitive(),
                        resolver.getRepositories()
                    ));
            }
        });
    }

    @Override
    public ConfigurationInternal getConsistentResolutionSource() {
        warnOnInvalidInternalAPIUsage("getConsistentResolutionSource()", ProperMethodUsage.RESOLVABLE);
        return consistentResolutionSource;
    }

    private Stream<DependencyConstraint> getConsistentResolutionConstraints() {
        if (consistentResolutionSource == null) {
            return Stream.empty();
        }
        assertThatConsistentResolutionIsPropertyConfigured();
        return consistentResolutionSource.getIncoming()
            .getResolutionResult()
            .getAllComponents()
            .stream()
            .map(this::registerConsistentResolutionConstraint)
            .filter(Objects::nonNull);
    }

    private void assertThatConsistentResolutionIsPropertyConfigured() {
        if (!consistentResolutionSource.isCanBeResolved()) {
            throw new InvalidUserCodeException("You can't use " + consistentResolutionSource + " as a consistent resolution source for " + this + " because it isn't a resolvable configuration.");
        }
        assertNoDependencyResolutionConsistencyCycle();
    }

    private void assertNoDependencyResolutionConsistencyCycle() {
        Set<ConfigurationInternal> sources = Sets.newLinkedHashSet();
        ConfigurationInternal src = this;
        while (src != null) {
            if (!sources.add(src)) {
                String cycle = sources.stream().map(Configuration::getName).collect(Collectors.joining(" -> ")) + " -> " + getName();
                throw new InvalidUserDataException("Cycle detected in consistent resolution sources: " + cycle);
            }
            src = src.getConsistentResolutionSource();
        }
    }

    @Nullable
    private DependencyConstraint registerConsistentResolutionConstraint(ResolvedComponentResult result) {
        if (result.getId() instanceof ModuleComponentIdentifier) {
            ModuleVersionIdentifier moduleVersion = result.getModuleVersion();
            DefaultDependencyConstraint constraint = DefaultDependencyConstraint.strictly(
                moduleVersion.getGroup(),
                moduleVersion.getName(),
                moduleVersion.getVersion());
            constraint.because(consistentResolutionReason);
            return constraint;
        }
        return null;
    }

    private void performPreResolveActions(ResolvableDependencies incoming) {
        DependencyResolutionListener dependencyResolutionListener = dependencyResolutionListeners.getSource();
        insideBeforeResolve = true;
        try {
            dependencyResolutionListener.beforeResolve(incoming);
        } finally {
            insideBeforeResolve = false;
        }
    }

    private void markReferencedProjectConfigurationsObserved(InternalState requestedState, ResolverResults results) {
        ProjectInternal consumingProject = domainObjectContext.getProject();
        ProjectState consumingProjectState = consumingProject == null ? null : consumingProject.getOwner();
        for (ResolvedProjectConfiguration projectResult : results.getResolvedLocalComponents().getResolvedProjectConfigurations()) {
            ProjectState targetProjectState = projectStateRegistry.stateFor(projectResult.getId());
            dependencyObservedBroadcast.dependencyObserved(consumingProjectState, targetProjectState, requestedState, projectResult);
        }
    }

    /**
     * Must be called from {@link #resolveExclusively(InternalState)} only.
     */
    private ResolveState resolveArtifactsIfRequired(ResolveState currentState) {
        if (currentState.state == ARTIFACTS_RESOLVED) {
            return currentState;
        }
        if (currentState.state != GRAPH_RESOLVED) {
            throw new IllegalStateException("Cannot resolve artifacts before graph has been resolved.");
        }
        ResolverResults graphResults = currentState.getCachedResolverResults();
        ResolverResults artifactResults = resolver.resolveArtifacts(DefaultConfiguration.this, graphResults);
        return new ArtifactsResolved(artifactResults);
    }

    @Override
    public TransformUpstreamDependenciesResolverFactory getDependenciesResolverFactory() {
        warnOnInvalidInternalAPIUsage("getDependenciesResolverFactory()", ProperMethodUsage.RESOLVABLE);
        if (dependenciesResolverFactory == null) {
            dependenciesResolverFactory = new DefaultTransformUpstreamDependenciesResolverFactory(getIdentity(), new DefaultResolutionResultProvider(), domainObjectContext, calculatedValueContainerFactory,
                (attributes, filter) -> {
                    ImmutableAttributes fullAttributes = attributesFactory.concat(configurationAttributes.asImmutable(), attributes);
                    return new ResolutionBackedFileCollection(
                        new SelectedArtifactsProvider(Specs.satisfyAll(), fullAttributes, filter, false, false, new VisitedArtifactsSetProvider()),
                        false,
                        new DefaultResolutionHost(),
                        taskDependencyFactory);
                });
        }
        return dependenciesResolverFactory;
    }

    @Override
    public void resetResolutionState() {
        warnOnInvalidInternalAPIUsage("resetResolutionState()", ProperMethodUsage.RESOLVABLE);
        currentResolveState.set(ResolveState.NOT_RESOLVED);
    }

    private ResolverResults getResultsForBuildDependencies() {
        ResolveState currentState = currentResolveState.get();
        if (currentState.state == UNRESOLVED) {
            throw new IllegalStateException("Cannot query results until resolution has happened.");
        }
        return currentState.getCachedResolverResults();
    }

    private ResolverResults resolveGraphForBuildDependenciesIfRequired() {
        if (getResolutionStrategy().resolveGraphToDetermineTaskDependencies()) {
            // Force graph resolution as this is required to calculate build dependencies
            return resolveToStateOrLater(GRAPH_RESOLVED).getCachedResolverResults();
        }

        ResolveState currentState = currentResolveState.update(initial -> {
            if (initial.state == UNRESOLVED) {
                // Traverse graph
                ResolverResults results = resolver.resolveBuildDependencies(DefaultConfiguration.this);
                markReferencedProjectConfigurationsObserved(BUILD_DEPENDENCIES_RESOLVED, results);
                return new BuildDependenciesResolved(results);
            } // Otherwise, already have a result, so reuse it
            return initial;
        });

        // Otherwise, already have a result, so reuse it
        return currentState.getCachedResolverResults();
    }

    private ResolverResults getResultsForArtifacts() {
        ResolveState currentState = currentResolveState.get();
        if (currentState.state != ARTIFACTS_RESOLVED) {
            // Do not validate that the current thread holds the project lock
            // Should instead assert that the results are available and fail if not
            currentState = resolveExclusively(ARTIFACTS_RESOLVED);
        }
        return currentState.getCachedResolverResults();
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        assertIsResolvable();
        context.add(intrinsicFiles);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskDependency getTaskDependencyFromProjectDependency(final boolean useDependedOn, final String taskName) {
        if (useDependedOn) {
            return new TasksFromProjectDependencies(taskName, this::getAllDependencies, taskDependencyFactory);
        } else {
            return new TasksFromDependentProjects(taskName, getName(), taskDependencyFactory);
        }
    }

    @Override
    public DependencySet getDependencies() {
        return dependencies;
    }

    @Override
    public DependencySet getAllDependencies() {
        if (allDependencies == null) {
            initAllDependencies();
        }
        return allDependencies;
    }

    @Override
    public boolean hasDependencies() {
        return !getAllDependencies().isEmpty();
    }

    @Override
    public int getEstimatedGraphSize() {
        // TODO #24641: Why are the numbers and operations here the way they are?
        //  Are they up-to-date? We should be able to test if these values are still optimal.
        int estimate = (int) (512 * Math.log(getAllDependencies().size()));
        return Math.max(10, estimate);
    }

    private synchronized void initAllDependencies() {
        if (allDependencies != null) {
            return;
        }
        inheritedDependencies = domainObjectCollectionFactory.newDomainObjectSet(Dependency.class, ownDependencies);
        for (Configuration configuration : this.extendsFrom) {
            inheritedDependencies.addCollection(configuration.getAllDependencies());
        }
        allDependencies = new DefaultDependencySet(Describables.of(displayName, "all dependencies"), this, inheritedDependencies);
    }

    @Override
    public DependencyConstraintSet getDependencyConstraints() {
        return dependencyConstraints;
    }

    @Override
    public DependencyConstraintSet getAllDependencyConstraints() {
        if (allDependencyConstraints == null) {
            initAllDependencyConstraints();
        }
        return allDependencyConstraints;
    }

    private synchronized void initAllDependencyConstraints() {
        if (allDependencyConstraints != null) {
            return;
        }
        inheritedDependencyConstraints = domainObjectCollectionFactory.newDomainObjectSet(DependencyConstraint.class, ownDependencyConstraints);
        for (Configuration configuration : this.extendsFrom) {
            inheritedDependencyConstraints.addCollection(configuration.getAllDependencyConstraints());
        }
        allDependencyConstraints = new DefaultDependencyConstraintSet(Describables.of(displayName, "all dependency constraints"), this, inheritedDependencyConstraints);
    }

    @Override
    public PublishArtifactSet getArtifacts() {
        return artifacts;
    }

    @Override
    public PublishArtifactSet getAllArtifacts() {
        initAllArtifacts();
        return allArtifacts;
    }

    private synchronized void initAllArtifacts() {
        if (allArtifacts != null) {
            return;
        }
        DisplayName displayName = Describables.of(this.displayName, "all artifacts");

        if (!canBeMutated && extendsFrom.isEmpty()) {
            // No further mutation is allowed and there's no parent: the artifact set corresponds to this configuration own artifacts
            this.allArtifacts = new DefaultPublishArtifactSet(displayName, ownArtifacts, fileCollectionFactory, taskDependencyFactory);
            return;
        }

        if (canBeMutated) {
            // If the configuration can still be mutated, we need to create a composite
            inheritedArtifacts = domainObjectCollectionFactory.newDomainObjectSet(PublishArtifact.class, ownArtifacts);
        }
        for (Configuration configuration : this.extendsFrom) {
            PublishArtifactSet allArtifacts = configuration.getAllArtifacts();
            if (inheritedArtifacts != null || !allArtifacts.isEmpty()) {
                if (inheritedArtifacts == null) {
                    // This configuration cannot be mutated, but some parent configurations provide artifacts
                    inheritedArtifacts = domainObjectCollectionFactory.newDomainObjectSet(PublishArtifact.class, ownArtifacts);
                }
                inheritedArtifacts.addCollection(allArtifacts);
            }
        }
        if (inheritedArtifacts != null) {
            this.allArtifacts = new DefaultPublishArtifactSet(displayName, inheritedArtifacts, fileCollectionFactory, taskDependencyFactory);
        } else {
            this.allArtifacts = new DefaultPublishArtifactSet(displayName, ownArtifacts, fileCollectionFactory, taskDependencyFactory);
        }
    }

    @Override
    public Set<ExcludeRule> getExcludeRules() {
        initExcludeRules();
        return Collections.unmodifiableSet(parsedExcludeRules);
    }

    @Override
    public Set<ExcludeRule> getAllExcludeRules() {
        Set<ExcludeRule> result = Sets.newLinkedHashSet();
        result.addAll(getExcludeRules());
        for (Configuration config : extendsFrom) {
            result.addAll(((ConfigurationInternal) config).getAllExcludeRules());
        }
        return result;
    }

    /**
     * Synchronize read access to excludes. Mutation does not need to be thread-safe.
     */
    private synchronized void initExcludeRules() {
        if (parsedExcludeRules == null) {
            NotationParser<Object, ExcludeRule> parser = ExcludeRuleNotationConverter.parser();
            parsedExcludeRules = Sets.newLinkedHashSet();
            for (Object excludeRule : excludeRules) {
                parsedExcludeRules.add(parser.parseNotation(excludeRule));
            }
        }
    }

    /**
     * Adds exclude rules to this configuration.
     * <p>
     * Usage: This method should only be called on resolvable or declarable configurations and should throw an exception if
     * called on a configuration that does not permit this usage.
     *
     * @param excludeRules the exclude rules to add.
     */
    public void setExcludeRules(Set<ExcludeRule> excludeRules) {
        warnOnInvalidInternalAPIUsage("setExcludeRules(Set)", ProperMethodUsage.DECLARABLE_AGAINST, ProperMethodUsage.RESOLVABLE);
        validateMutation(MutationType.DEPENDENCIES);
        parsedExcludeRules = null;
        this.excludeRules.clear();
        this.excludeRules.addAll(excludeRules);
    }

    @Override
    public DefaultConfiguration exclude(Map<String, String> excludeRuleArgs) {
        validateMutation(MutationType.DEPENDENCIES);
        parsedExcludeRules = null;
        excludeRules.add(excludeRuleArgs);
        return this;
    }

    @Deprecated // TODO:Finalize Upload Removal - Issue #21439
    @Override
    public String getUploadTaskName() {
        return Configurations.uploadTaskName(getName());
    }

    @Override
    public Describable asDescribable() {
        return displayName;
    }

    @Override
    public String getDisplayName() {
        return displayName.getDisplayName();
    }

    @Override
    public ResolvableDependencies getIncoming() {
        return resolvableDependencies;
    }

    @Override
    public ConfigurationPublications getOutgoing() {
        return outgoing;
    }

    @Override
    public void collectVariants(VariantVisitor visitor) {
        outgoing.collectVariants(visitor);
    }

    @Override
    public boolean isCanBeMutated() {
        return canBeMutated;
    }

    @Override
    public void preventFromFurtherMutation() {
        preventFromFurtherMutation(false);
    }

    @Override
    public List<? extends GradleException> preventFromFurtherMutationLenient() {
        return preventFromFurtherMutation(true);
    }

    private List<? extends GradleException> preventFromFurtherMutation(boolean lenient) {
        // TODO This should use the same `MutationValidator` infrastructure that we use for other mutation types
        if (canBeMutated) {
            AttributeContainerInternal delegatee = configurationAttributes.asImmutable();
            configurationAttributes = new ImmutableAttributeContainerWithErrorMessage(delegatee, this.displayName);
            outgoing.preventFromFurtherMutation();
            canBeMutated = false;

            preventUsageMutation();

            // We will only check unique attributes if this configuration is consumable, not resolvable, and has attributes itself
            if (mustHaveUniqueAttributes(this) && !this.getAttributes().isEmpty()) {
                return ensureUniqueAttributes(lenient);
            }

        }
        return Collections.emptyList();
    }

    private List<? extends GradleException> ensureUniqueAttributes(boolean lenient) {
        final Set<? extends ConfigurationInternal> all = (configurationsProvider != null) ? configurationsProvider.getAll() : null;
        if (all != null) {
            final Collection<? extends Capability> allCapabilities = allCapabilitiesIncludingDefault(this);

            final Predicate<ConfigurationInternal> isDuplicate = otherConfiguration -> hasSameCapabilitiesAs(allCapabilities, otherConfiguration) && hasSameAttributesAs(otherConfiguration);
            List<String> collisions = all.stream()
                .filter(c -> c != this)
                .filter(this::mustHaveUniqueAttributes)
                .filter(c -> !c.isCanBeMutated())
                .filter(isDuplicate)
                .map(ResolveContext::getDisplayName)
                .collect(Collectors.toList());
            if (!collisions.isEmpty()) {
                DocumentedFailure.Builder builder = DocumentedFailure.builder();
                String advice = "Consider adding an additional attribute to one of the configurations to disambiguate them.";
                if (!lenient) {
                    advice += "  Run the 'outgoingVariants' task for more details.";
                }
                GradleException gradleException = builder.withSummary("Consumable configurations with identical capabilities within a project (other than the default configuration) must have unique attributes, but " + getDisplayName() + " and " + collisions + " contain identical attribute sets.")
                    .withAdvice(advice)
                    .withUserManual("upgrading_version_7", "unique_attribute_sets")
                    .build();
                if (lenient) {
                    return Collections.singletonList(gradleException);
                } else {
                    throw gradleException;
                }
            }
        }
        return Collections.emptyList();
    }

    /**
     * The only configurations which must have unique attributes are those which are consumable (and not also resolvable, legacy configurations),
     * excluding the default configuration.
     *
     * @param configuration the configuration to inspect
     * @return {@code true} if the given configuration must have unique attributes; {@code false} otherwise
     */
    private boolean mustHaveUniqueAttributes(Configuration configuration) {
        return configuration.isCanBeConsumed() && !configuration.isCanBeResolved() && !Dependency.DEFAULT_CONFIGURATION.equals(configuration.getName());
    }

    private Collection<? extends Capability> allCapabilitiesIncludingDefault(Configuration conf) {
        if (conf.getOutgoing().getCapabilities().isEmpty()) {
            Project project = domainObjectContext.getProject();
            if (project == null) {
                throw new IllegalStateException("Project is null for configuration '" + conf.getName() + "'.");
            }
            return Collections.singleton(new ProjectDerivedCapability(project));
        } else {
            return conf.getOutgoing().getCapabilities();
        }
    }

    private boolean hasSameCapabilitiesAs(final Collection<? extends Capability> allMyCapabilities, ConfigurationInternal other) {
        final Collection<? extends Capability> allOtherCapabilities = allCapabilitiesIncludingDefault(other);
        //noinspection SuspiciousMethodCalls
        return allMyCapabilities.size() == allOtherCapabilities.size() && allMyCapabilities.containsAll(allOtherCapabilities);
    }

    private boolean hasSameAttributesAs(ConfigurationInternal other) {
        return other.getAttributes().asMap().equals(getAttributes().asMap());
    }

    @Override
    public void outgoing(Action<? super ConfigurationPublications> action) {
        action.execute(outgoing);
    }

    @Override
    public ConfigurationInternal copy() {
        warnOnDeprecatedUsage("copy()", ProperMethodUsage.RESOLVABLE);
        return createCopy(getDependencies(), getDependencyConstraints());
    }

    @Override
    public Configuration copyRecursive() {
        warnOnDeprecatedUsage("copyRecursive()", ProperMethodUsage.RESOLVABLE);
        return createCopy(getAllDependencies(), getAllDependencyConstraints());
    }

    @Override
    public Configuration copy(Spec<? super Dependency> dependencySpec) {
        warnOnDeprecatedUsage("copy(Spec)", ProperMethodUsage.RESOLVABLE);
        return createCopy(CollectionUtils.filter(getDependencies(), dependencySpec), getDependencyConstraints());
    }

    @Override
    public Configuration copyRecursive(Spec<? super Dependency> dependencySpec) {
        warnOnDeprecatedUsage("copyRecursive(Spec)", ProperMethodUsage.RESOLVABLE);
        return createCopy(CollectionUtils.filter(getAllDependencies(), dependencySpec), getAllDependencyConstraints());
    }

    /**
     * Instead of copying a configuration's roles outright, we allow copied configurations
     * to assume any role. However, any roles which were previously disabled will become
     * deprecated in the copied configuration. In 9.0, we will update this to copy
     * roles and deprecations without modification. Or, better yet, we will remove support
     * for copying configurations altogether.
     *
     * This means the copy created is <strong>NOT</strong> a strictly identical copy of the original, as the role
     * will be not only a different instance, but also may return different deprecation values.
     */
    private DefaultConfiguration createCopy(Set<Dependency> dependencies, Set<DependencyConstraint> dependencyConstraints) {
        // Begin by allowing everything, and setting deprecations for disallowed roles in a new role implementation
        boolean deprecateConsumption = !canBeConsumed || consumptionDeprecated;
        boolean deprecateResolution = !canBeResolved || resolutionDeprecated;
        boolean deprecateDeclarationAgainst = !canBeDeclaredAgainst || declarationDeprecated;
        ConfigurationRole adjustedCurrentUsage = new DefaultConfigurationRole(
            "adjusted current usage with deprecations",
            true, true, true,
            deprecateConsumption, deprecateResolution, deprecateDeclarationAgainst
        );


        DefaultConfiguration copiedConfiguration = newConfiguration(adjustedCurrentUsage);
        // state, cachedResolvedConfiguration, and extendsFrom intentionally not copied - must re-resolve copy
        // copying extendsFrom could mess up dependencies when copy was re-resolved

        copiedConfiguration.visible = visible;
        copiedConfiguration.transitive = transitive;
        copiedConfiguration.description = description;

        copiedConfiguration.defaultDependencyActions = defaultDependencyActions;
        copiedConfiguration.withDependencyActions = withDependencyActions;
        copiedConfiguration.dependencyResolutionListeners = dependencyResolutionListeners.copy();

        copiedConfiguration.declarationAlternatives = declarationAlternatives;
        copiedConfiguration.resolutionAlternatives = resolutionAlternatives;

        copiedConfiguration.getArtifacts().addAll(getAllArtifacts());

        if (!configurationAttributes.isEmpty()) {
            for (Attribute<?> attribute : configurationAttributes.keySet()) {
                Object value = configurationAttributes.getAttribute(attribute);
                copiedConfiguration.getAttributes().attribute(Cast.uncheckedNonnullCast(attribute), value);
            }
        }

        // todo An ExcludeRule is a value object but we don't enforce immutability for DefaultExcludeRule as strong as we
        // should (we expose the Map). We should provide a better API for ExcludeRule (I don't want to use unmodifiable Map).
        // As soon as DefaultExcludeRule is truly immutable, we don't need to create a new instance of DefaultExcludeRule.
        for (ExcludeRule excludeRule : getAllExcludeRules()) {
            copiedConfiguration.excludeRules.add(new DefaultExcludeRule(excludeRule.getGroup(), excludeRule.getModule()));
        }

        DomainObjectSet<Dependency> copiedDependencies = copiedConfiguration.getDependencies();
        for (Dependency dependency : dependencies) {
            copiedDependencies.add(dependency.copy());
        }
        DomainObjectSet<DependencyConstraint> copiedDependencyConstraints = copiedConfiguration.getDependencyConstraints();
        for (DependencyConstraint dependencyConstraint : dependencyConstraints) {
            copiedDependencyConstraints.add(((DependencyConstraintInternal) dependencyConstraint).copy());
        }
        return copiedConfiguration;
    }

    private DefaultConfiguration newConfiguration(ConfigurationRole role) {
        String newName = getNameWithCopySuffix();
        DetachedConfigurationsProvider configurationsProvider = new DetachedConfigurationsProvider();
        RootComponentMetadataBuilder rootComponentMetadataBuilder = this.rootComponentMetadataBuilder.withConfigurationsProvider(configurationsProvider);

        Factory<ResolutionStrategyInternal> childResolutionStrategy = resolutionStrategy != null ? Factories.constant(resolutionStrategy.copy()) : resolutionStrategyFactory;
        DefaultConfiguration copiedConfiguration = defaultConfigurationFactory.create(
            newName,
            configurationsProvider,
            childResolutionStrategy,
            rootComponentMetadataBuilder,
            role
        );
        configurationsProvider.setTheOnlyConfiguration(copiedConfiguration);
        return copiedConfiguration;
    }

    private String getNameWithCopySuffix() {
        int count = copyCount.incrementAndGet();
        String copyName = name + "Copy";
        return count == 1
            ? copyName
            : copyName + count;
    }

    @Override
    public Configuration copy(Closure dependencySpec) {
        return copy(Specs.convertClosureToSpec(dependencySpec));
    }

    @Override
    public Configuration copyRecursive(Closure dependencySpec) {
        return copyRecursive(Specs.convertClosureToSpec(dependencySpec));
    }

    @Override
    public ResolutionStrategyInternal getResolutionStrategy() {
        if (resolutionStrategy == null) {
            resolutionStrategy = resolutionStrategyFactory.create();
            resolutionStrategy.setMutationValidator(this);
            resolutionStrategyFactory = null;
        }
        return resolutionStrategy;
    }

    @Override
    public RootComponentMetadataBuilder.RootComponentState toRootComponent() {
        warnOnInvalidInternalAPIUsage("toRootComponent()", ProperMethodUsage.RESOLVABLE);
        return rootComponentMetadataBuilder.toRootComponent(getName());
    }

    @Override
    public List<? extends DependencyMetadata> getSyntheticDependencies() {
        warnOnInvalidInternalAPIUsage("getSyntheticDependencies()", ProperMethodUsage.RESOLVABLE);
        return syntheticDependencies.get();
    }

    private List<? extends DependencyMetadata> generateSyntheticDependencies() {
        ComponentIdentifier componentIdentifier = componentIdentifierFactory.createComponentIdentifier(getModule());

        Stream<LocalComponentDependencyMetadata> dependencyLockingConstraintMetadata = Stream.empty();
        if (getResolutionStrategy().isDependencyLockingEnabled()) {
            DependencyLockingState dependencyLockingState = dependencyLockingProvider.loadLockState(name);
            boolean strict = dependencyLockingState.mustValidateLockState();
            dependencyLockingConstraintMetadata = dependencyLockingState.getLockedDependencies().stream().map(lockedDependency -> {
                String lockedVersion = lockedDependency.getVersion();
                VersionConstraint versionConstraint = strict
                    ? DefaultMutableVersionConstraint.withStrictVersion(lockedVersion)
                    : DefaultMutableVersionConstraint.withVersion(lockedVersion);
                ModuleComponentSelector selector = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(lockedDependency.getGroup(), lockedDependency.getModule()), versionConstraint);
                return new LocalComponentDependencyMetadata(
                    selector, ImmutableAttributes.EMPTY, null, Collections.emptyList(),  Collections.emptyList(),
                    false, false, false, true, false, true, getLockReason(strict, lockedVersion)
                );
            });
        }

        Stream<LocalComponentDependencyMetadata> consistentResolutionConstraintMetadata = getConsistentResolutionConstraints().map(dc -> {
            ModuleComponentSelector selector = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(dc.getGroup(), dc.getName()), dc.getVersionConstraint());
            return new LocalComponentDependencyMetadata(
                selector, ImmutableAttributes.EMPTY, null, Collections.emptyList(), Collections.emptyList(),
                false, false, false, true, false, true, dc.getReason()
            );
        });

        return Stream.concat(dependencyLockingConstraintMetadata, consistentResolutionConstraintMetadata)
                     .collect(ImmutableList.toImmutableList());
    }

    private String getLockReason(boolean strict, String lockedVersion) {
        if (strict) {
            return "dependency was locked to version '" + lockedVersion + "'";
        }
        return "dependency was locked to version '" + lockedVersion + "' (update/lenient mode)";
    }

    @Override
    public Path getProjectPath() {
        return projectPath;
    }

    @Override
    public Path getIdentityPath() {
        return identityPath;
    }

    @Override
    public DomainObjectContext getDomainObjectContext() {
        return domainObjectContext;
    }

    @Override
    public Configuration resolutionStrategy(Closure closure) {
        configure(closure, getResolutionStrategy());
        return this;
    }

    @Override
    public Configuration resolutionStrategy(Action<? super ResolutionStrategy> action) {
        action.execute(getResolutionStrategy());
        return this;
    }

    @Override
    public void addMutationValidator(MutationValidator validator) {
        childMutationValidators.add(validator);
    }

    @Override
    public void removeMutationValidator(MutationValidator validator) {
        childMutationValidators.remove(validator);
    }

    private void validateParentMutation(MutationType type) {
        // Strategy changes in a parent configuration do not affect this configuration, or any of its children, in any way
        if (type == MutationType.STRATEGY) {
            return;
        }

        preventIllegalParentMutation(type);
        markAsModified(type);
        notifyChildren(type);
    }

    @Override
    public void validateMutation(MutationType type) {
        preventIllegalMutation(type);
        markAsModified(type);
        notifyChildren(type);
    }

    private void preventIllegalParentMutation(MutationType type) {
        // TODO Deprecate and eventually prevent these mutations in parent when already resolved
        if (type == MutationType.DEPENDENCY_ATTRIBUTES) {
            return;
        }

        InternalState resolvedState = currentResolveState.get().state;
        if (resolvedState == ARTIFACTS_RESOLVED) {
            throw new InvalidUserDataException(String.format("Cannot change %s of parent of %s after it has been resolved", type, getDisplayName()));
        } else if (resolvedState == GRAPH_RESOLVED) {
            if (type == MutationType.DEPENDENCIES) {
                throw new InvalidUserDataException(String.format("Cannot change %s of parent of %s after task dependencies have been resolved", type, getDisplayName()));
            }
        }
    }

    private void preventIllegalMutation(MutationType type) {
        // TODO: Deprecate and eventually prevent these mutations when already resolved
        if (type == MutationType.DEPENDENCY_ATTRIBUTES) {
            assertIsDeclarable();
            return;
        }

        InternalState resolvedState = currentResolveState.get().state;
        if (resolvedState == ARTIFACTS_RESOLVED) {
            // The public result for the configuration has been calculated.
            // It is an error to change anything that would change the dependencies or artifacts
            throw new InvalidUserDataException(String.format("Cannot change %s of dependency %s after it has been resolved.", type, getDisplayName()));
        } else if (resolvedState == GRAPH_RESOLVED) {
            // The task dependencies for the configuration have been calculated using Configuration.getBuildDependencies().
            throw new InvalidUserDataException(String.format("Cannot change %s of dependency %s after task dependencies have been resolved", type, getDisplayName()));
        } else if (observedState == GRAPH_RESOLVED || observedState == ARTIFACTS_RESOLVED) {
            // The configuration has been used in a resolution, and it is an error for build logic to change any dependencies,
            // exclude rules or parent configurations (values that will affect the resolved graph).
            if (type != MutationType.STRATEGY) {
                String extraMessage = insideBeforeResolve ? " Use 'defaultDependencies' instead of 'beforeResolve' to specify default dependencies for a configuration." : "";
                throw new InvalidUserDataException(String.format("Cannot change %s of dependency %s after it has been included in dependency resolution.%s", type, getDisplayName(), extraMessage));
            }
        }

        if (type == MutationType.USAGE) {
            assertUsageIsMutable();
        }
    }

    private void markAsModified(MutationType type) {
        // TODO: Should not be ignoring DEPENDENCY_ATTRIBUTE modifications after resolve
        if (type == MutationType.DEPENDENCY_ATTRIBUTES) {
            return;
        }
        // Strategy mutations will not require a re-resolve
        if (type == MutationType.STRATEGY) {
            return;
        }
        dependenciesModified = true;
    }

    private void notifyChildren(MutationType type) {
        // Notify child configurations
        for (MutationValidator validator : childMutationValidators) {
            validator.validateMutation(type);
        }
    }

    private ConfigurationIdentity getIdentity() {
        String name = getName();
        String projectPath = domainObjectContext.getProjectPath() == null ? null : domainObjectContext.getProjectPath().toString();
        String buildPath = domainObjectContext.getBuildPath().toString();
        return new DefaultConfigurationIdentity(buildPath, projectPath, name);
    }

    private boolean isProperUsage(boolean allowDeprecated, ProperMethodUsage... properUsages) {
        for (ProperMethodUsage properUsage : properUsages) {
            if (properUsage.isProperUsage(this, allowDeprecated)) {
                return true;
            }
        }
        return false;
    }

    private void warnOnInvalidInternalAPIUsage(String methodName, ProperMethodUsage... properUsages) {
        warnOnDeprecatedUsage(methodName, true, properUsages);
    }

    private void warnOnDeprecatedUsage(String methodName, ProperMethodUsage... properUsages) {
        warnOnDeprecatedUsage(methodName, false, properUsages);
    }

    private void warnOnDeprecatedUsage(String methodName, boolean allowDeprecated, ProperMethodUsage... properUsages) {
        if (!isProperUsage(allowDeprecated, properUsages)) {
            String msgTemplate = "Calling configuration method '%s' is deprecated for configuration '%s', which has permitted usage(s):\n" +
                    "%s\n" +
                    "This method is only meant to be called on configurations which allow the %susage(s): '%s'.";
            String currentUsageDesc = UsageDescriber.describeCurrentUsage(this);
            String properUsageDesc = ProperMethodUsage.summarizeProperUsage(properUsages);

            DeprecationLogger.deprecateBehaviour(String.format(msgTemplate, methodName, getName(), currentUsageDesc, allowDeprecated ? "" : "(non-deprecated) ", properUsageDesc))
                    .willBeRemovedInGradle9()
                    .withUpgradeGuideSection(8, "deprecated_configuration_usage")
                    .nagUser();
        }
    }

    private static class ConfigurationDescription implements Describable {
        private final Path identityPath;

        ConfigurationDescription(Path identityPath) {
            this.identityPath = identityPath;
        }

        @Override
        public String getDisplayName() {
            return "configuration '" + identityPath + "'";
        }
    }

    private static class DefaultConfigurationIdentity implements ConfigurationIdentity {
        private final String buildPath;
        private final String projectPath;
        private final String name;

        public DefaultConfigurationIdentity(String buildPath, @Nullable String projectPath, String name) {
            this.buildPath = buildPath;
            this.projectPath = projectPath;
            this.name = name;
        }

        @Override
        public String getBuildPath() {
            return buildPath;
        }

        @Nullable
        @Override
        public String getProjectPath() {
            return projectPath;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            Path path = Path.path(buildPath);
            if (projectPath != null) {
                path = path.append(Path.path(projectPath));
            }
            path = path.child(name);
            return "Configuration '" + path.toString() + "'";
        }
    }

    private class DefaultResolutionResultProvider implements ResolutionResultProvider<ResolutionResult> {

        @Override
        public ResolutionResult getTaskDependencyValue() {
            return new DefaultResolutionResult(getResultsForBuildDependencies().getVisitedGraph().getResolutionResult());
        }

        @Override
        public ResolutionResult getValue() {
            return new DefaultResolutionResult(getResultsForArtifacts().getVisitedGraph().getResolutionResult());
        }
    }

    private class VisitedArtifactsSetProvider implements ResolutionResultProvider<VisitedArtifactSet> {

        @Override
        public VisitedArtifactSet getTaskDependencyValue() {
            assertIsResolvable();
            return resolveGraphForBuildDependenciesIfRequired().getVisitedArtifacts();
        }

        @Override
        public VisitedArtifactSet getValue() {
            assertIsResolvable();
            ResolveState currentState = resolveToStateOrLater(ARTIFACTS_RESOLVED);
            return currentState.getCachedResolverResults().getVisitedArtifacts();
        }
    }

    private static class SelectedArtifactsProvider implements ResolutionResultProvider<SelectedArtifactSet> {
        private final Spec<? super Dependency> dependencySpec;
        private final AttributeContainerInternal viewAttributes;
        private final Spec<? super ComponentIdentifier> componentSpec;
        private final boolean allowNoMatchingVariants;
        private final boolean selectFromAllVariants;
        private final ResolutionResultProvider<VisitedArtifactSet> resultProvider;

        public SelectedArtifactsProvider(
            Spec<? super Dependency> dependencySpec,
            AttributeContainerInternal viewAttributes,
            Spec<? super ComponentIdentifier> componentSpec,
            boolean allowNoMatchingVariants,
            boolean selectFromAllVariants,
            ResolutionResultProvider<VisitedArtifactSet> resultProvider
        ) {
            this.dependencySpec = dependencySpec;
            this.viewAttributes = viewAttributes;
            this.componentSpec = componentSpec;
            this.allowNoMatchingVariants = allowNoMatchingVariants;
            this.selectFromAllVariants = selectFromAllVariants;
            this.resultProvider = resultProvider;
        }

        @Override
        public SelectedArtifactSet getTaskDependencyValue() {
            ArtifactSelectionSpec artifactSpec = new ArtifactSelectionSpec(viewAttributes.asImmutable(), componentSpec, selectFromAllVariants, allowNoMatchingVariants);
            return resultProvider.getTaskDependencyValue().select(dependencySpec, artifactSpec);
        }

        @Override
        public SelectedArtifactSet getValue() {
            ArtifactSelectionSpec artifactSpec = new ArtifactSelectionSpec(viewAttributes.asImmutable(), componentSpec, selectFromAllVariants, allowNoMatchingVariants);
            return resultProvider.getValue().select(dependencySpec, artifactSpec);
        }
    }

    private void assertIsResolvable() {
        if (!canBeResolved) {
            throw new IllegalStateException("Resolving dependency configuration '" + name + "' is not allowed as it is defined as 'canBeResolved=false'.\nInstead, a resolvable ('canBeResolved=true') dependency configuration that extends '" + name + "' should be resolved.");
        }
    }

    private void assertIsDeclarable() {
        if (!canBeDeclaredAgainst) {
            throw new IllegalStateException("Declaring dependencies for configuration '" + name + "' is not allowed as it is defined as 'canBeDeclared=false'.");
        }
    }

    @Override
    protected void assertCanCarryBuildDependencies() {
        assertIsResolvable();
    }

    @Override
    public AttributeContainerInternal getAttributes() {
        return configurationAttributes;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote Usage: This method should only be called on consumable or resolvable configurations and will emit a deprecation warning if
     * called on a configuration that does not permit this usage, or has had allowed this usage but marked it as deprecated.
     */
    @Override
    public Configuration attributes(Action<? super AttributeContainer> action) {
        warnOnDeprecatedUsage("attributes(Action)", ProperMethodUsage.CONSUMABLE, ProperMethodUsage.RESOLVABLE);
        action.execute(configurationAttributes);
        return this;
    }

    @Override
    public void preventUsageMutation() {
        usageCanBeMutated = false;
    }

    @SuppressWarnings("deprecation")
    private void assertUsageIsMutable() {
        if (!usageCanBeMutated) {
            // Don't print role message for legacy role - users might not have actively chosen this role
            if (roleAtCreation != ConfigurationRoles.LEGACY) {
                throw new GradleException(
                        String.format("Cannot change the allowed usage of %s, as it was locked upon creation to the role: '%s'.\n" +
                                "This role permits the following usage:\n" +
                                "%s\n" +
                                "Ideally, each configuration should be used for a single purpose.",
                                getDisplayName(), roleAtCreation.getName(), roleAtCreation.describeUsage()));
            } else {
                throw new GradleException(String.format("Cannot change the allowed usage of %s, as it has been locked.", getDisplayName()));
            }
        }
    }

    private void maybeWarnOnChangingUsage(String usage, boolean current) {
        if (!isSpecialCaseOfChangingUsage(usage, current)) {
            String msgTemplate = "Allowed usage is changing for %s, %s. Ideally, usage should be fixed upon creation.";
            String changingUsage = usage + " was " + !current + " and is now " + current;

            DeprecationLogger.deprecateBehaviour(String.format(msgTemplate, getDisplayName(), changingUsage))
                    .withAdvice("Usage should be fixed upon creation.")
                    .willBeRemovedInGradle9()
                    .withUpgradeGuideSection(8, "configurations_allowed_usage")
                    .nagUser();
        }
    }

    private void maybeWarnOnRedundantUsageActivation(String usage, String method) {
        if (!isSpecialCaseOfRedundantUsageActivation()) {
            String msgTemplate = "The %s usage is already allowed on %s.";
            DeprecationLogger.deprecateBehaviour(String.format(msgTemplate, usage, getDisplayName()))
                    .withAdvice(String.format("Remove the call to %s, it has no effect.", method))
                    .willBeRemovedInGradle9()
                    .withUpgradeGuideSection(8, "redundant_configuration_usage_activation")
                    .nagUser();
        }
    }

    /**
     * This is a temporary method that decides if a usage change is a known/supported special case, where a deprecation warning message
     * should not be emitted.
     * <p>
     * Many of these exceptions are needed to avoid spamming deprecations warnings whenever the Kotlin plugin is used.  This method is
     * temporary as the eventual goal is that all configuration usage be specified upon creation and immutable thereafter.
     * <p>
     * <ol>
     *     <li>While {#roleAtCreation} is {@code null}, we are still initializing, so we should NOT warn.</li>
     *     <li>Changes to the usage of the detached configurations should NOT warn (this done by the Kotlin plugin).</li>
     *     <li>Configurations with a legacy role should NOT warn when changing usage,
since users cannot create non-legacy configurations and there is no current public API for setting roles upon creation</li>
     *     <li>Setting consumable usage to false on the {@code apiElements} and {@code runtimeElements} configurations should NOT warn (this is done by the Kotlin plugin).</li>
     *     <li>All other usage changes should warn.</li>
     * </ol>
     *
     * @param usage the name usage that is being changed
     * @param current the current value of the usage after the change
     *
     * @return {@code true} if the usage change is a known special case; {@code false} otherwise
     */
    private boolean isSpecialCaseOfChangingUsage(String usage, boolean current) {
        return isInitializing() || isDetachedConfiguration() || isInLegacyRole() || isPermittedConfigurationForUsageChange(usage, current);
    }

    /**
     * This is a temporary method that decides if a redundant usage activation is a known/supported special case,
     * where a deprecation warning message should not be emitted.
     * <p>
     * These exceptions are needed to avoid spamming deprecations warnings whenever some important 3rd party plugins like
     * Kotlin or Android are used.
     * <p>
     * <ol>
     *     <li>Redundant activation of a usage of a detached configurations should NOT warn (this done by the Kotlin plugin).</li>
     *     <li>Configurations with a legacy role should NOT warn during redundant usage activation,
     since users cannot create non-legacy configurations and there is no current public API for setting roles upon creation</li>
     *     <li>All other usage changes should warn.</li>
     * </ol>
     *
     * @return {@code true} if the usage change is a known special case; {@code false} otherwise
     */
    private boolean isSpecialCaseOfRedundantUsageActivation() {
        return isInLegacyRole() || isDetachedConfiguration() || isPermittedConfigurationForRedundantActivation();
    }

    private boolean isInitializing() {
        return roleAtCreation == null;
    }

    private boolean isDetachedConfiguration() {
        return this.configurationsProvider instanceof DetachedConfigurationsProvider;
    }

    @SuppressWarnings("deprecation")
    private boolean isInLegacyRole() {
        return roleAtCreation == ConfigurationRoles.LEGACY;
    }

    /**
     * Determine if this is a configuration that is permitted to change its usage, to support important 3rd party
     * plugins such as Kotlin that do this.
     * <p>
     * This method is temporary, so the duplication of the configuration names defined in
     * {@link JvmConstants}, which are not available to be referenced directly from here, is unfortunate, but not a showstopper.
     *
     * @return {@code true} if this is a configuration that is permitted to change its usage; {@code false} otherwise
     */
    @SuppressWarnings("JavadocReference")
    private boolean isPermittedConfigurationForUsageChange(String usage, boolean current) {
        return name.equals("apiElements") || name.equals("runtimeElements") && usage.equals("consumable") && !current;
    }

    /**
     * Determine if this is a configuration that is permitted to redundantly activate usage, to support important 3rd party
     * plugins such as Kotlin that do this.
     * <p>
     * This method is temporary, so the duplication of the configuration names defined in
     * {@link JvmConstants}, which are not available to be referenced directly from here, is unfortunate, but not a showstopper.
     *
     * @return {@code true} if this is a configuration that is permitted to redundantly activate usage; {@code false} otherwise
     */
    @SuppressWarnings("JavadocReference")
    private boolean isPermittedConfigurationForRedundantActivation() {
        return name.equals("runtimeClasspath") || name.equals("testFixturesRuntimeClasspath") || name.endsWith("testRuntimeClasspath") || name.endsWith("TestRuntimeClasspath");
    }

    @Override
    public boolean isDeprecatedForConsumption() {
        return consumptionDeprecated;
    }

    @Override
    public boolean isDeprecatedForResolution() {
        return resolutionDeprecated;
    }

    @Override
    public boolean isDeprecatedForDeclarationAgainst() {
        return declarationDeprecated;
    }

    @Override
    public boolean isCanBeConsumed() {
        return canBeConsumed;
    }

    @Override
    public void setCanBeConsumed(boolean allowed) {
        if (canBeConsumed != allowed) {
            validateMutation(MutationType.USAGE);
            canBeConsumed = allowed;
            maybeWarnOnChangingUsage("consumable", allowed);
        } else if (canBeConsumed && allowed) {
            maybeWarnOnRedundantUsageActivation("consumable", "setCanBeConsumed(true)");
        }
    }

    @Override
    public boolean isCanBeResolved() {
        return canBeResolved;
    }

    @Override
    public void setCanBeResolved(boolean allowed) {
        if (canBeResolved != allowed) {
            validateMutation(MutationType.USAGE);
            canBeResolved = allowed;
            maybeWarnOnChangingUsage("resolvable", allowed);
        } else if (canBeResolved && allowed) {
            maybeWarnOnRedundantUsageActivation("resolvable", "setCanBeResolved(true)");
        }
    }

    @Override
    public boolean isCanBeDeclared() {
        return canBeDeclaredAgainst;
    }

    @Override
    public void setCanBeDeclared(boolean allowed) {
        if (canBeDeclaredAgainst != allowed) {
            validateMutation(MutationType.USAGE);
            canBeDeclaredAgainst = allowed;
            maybeWarnOnChangingUsage("declarable", allowed);
        } else if (canBeDeclaredAgainst && allowed) {
            maybeWarnOnRedundantUsageActivation("declarable", "setCanBeDeclared(true)");
        }
    }

    @VisibleForTesting
    ListenerBroadcast<DependencyResolutionListener> getDependencyResolutionListeners() {
        return dependencyResolutionListeners;
    }

    @Override
    public List<String> getDeclarationAlternatives() {
        return declarationAlternatives;
    }

    @Override
    public List<String> getResolutionAlternatives() {
        return resolutionAlternatives;
    }

    @Override
    public void addDeclarationAlternatives(String... alternativesForDeclaring) {
        this.declarationAlternatives = ImmutableList.<String>builder()
            .addAll(declarationAlternatives)
            .addAll(Arrays.asList(alternativesForDeclaring))
            .build();
    }

    @Override
    public void addResolutionAlternatives(String... alternativesForResolving) {
        this.resolutionAlternatives = ImmutableList.<String>builder()
            .addAll(resolutionAlternatives)
            .addAll(Arrays.asList(alternativesForResolving))
            .build();
    }

    @Override
    public Configuration shouldResolveConsistentlyWith(Configuration versionsSource) {
        warnOnDeprecatedUsage("shouldResolveConsistentlyWith(Configuration)", ProperMethodUsage.RESOLVABLE);
        this.consistentResolutionSource = (ConfigurationInternal) versionsSource;
        this.consistentResolutionReason = "version resolved in " + versionsSource + " by consistent resolution";
        return this;
    }

    @Override
    public Configuration disableConsistentResolution() {
        warnOnDeprecatedUsage("disableConsistentResolution()", ProperMethodUsage.RESOLVABLE);
        this.consistentResolutionSource = null;
        this.consistentResolutionReason = null;
        return this;
    }

    @Override
    public ConfigurationRole getRoleAtCreation() {
        return roleAtCreation;
    }

    private abstract static class ResolveState {
        static final ResolveState NOT_RESOLVED = new ResolveState(UNRESOLVED) {
            @Override
            public ResolvedConfiguration getResolvedConfiguration() {
                throw new IllegalStateException();
            }

            @Override
            public ResolverResults getCachedResolverResults() {
                throw new IllegalStateException();
            }

            @Override
            public boolean hasError() {
                return false;
            }
        };

        final InternalState state;

        ResolveState(InternalState state) {
            this.state = state;
        }

        abstract boolean hasError();

        public abstract ResolvedConfiguration getResolvedConfiguration();

        public abstract ResolverResults getCachedResolverResults();
    }

    private static class WithResults extends ResolveState {
        final ResolverResults cachedResolverResults;

        WithResults(InternalState state, ResolverResults cachedResolverResults) {
            super(state);
            this.cachedResolverResults = cachedResolverResults;
        }

        @Override
        boolean hasError() {
            return cachedResolverResults.getVisitedGraph().hasAnyFailure();
        }

        @Override
        public ResolverResults getCachedResolverResults() {
            return cachedResolverResults;
        }

        @Override
        public ResolvedConfiguration getResolvedConfiguration() {
            return cachedResolverResults.getResolvedConfiguration();
        }
    }

    private static class BuildDependenciesResolved extends WithResults {
        public BuildDependenciesResolved(ResolverResults results) {
            super(BUILD_DEPENDENCIES_RESOLVED, results);
        }
    }

    private static class GraphResolved extends WithResults {
        public GraphResolved(ResolverResults cachedResolverResults) {
            super(GRAPH_RESOLVED, cachedResolverResults);
        }
    }

    private static class ArtifactsResolved extends WithResults {
        public ArtifactsResolved(ResolverResults results) {
            super(ARTIFACTS_RESOLVED, results);
        }
    }

    private DefaultArtifactCollection artifactCollection(AttributeContainerInternal attributes, Spec<? super ComponentIdentifier> componentFilter, boolean lenient, boolean allowNoMatchingVariants, boolean selectFromAllVariants) {
        DefaultResolutionHost failureHandler = new DefaultResolutionHost();
        ResolutionBackedFileCollection files = new ResolutionBackedFileCollection(
            new SelectedArtifactsProvider(Specs.satisfyAll(), attributes, componentFilter, allowNoMatchingVariants, selectFromAllVariants, new VisitedArtifactsSetProvider()), lenient, failureHandler, taskDependencyFactory
        );
        return new DefaultArtifactCollection(files, lenient, failureHandler, calculatedValueContainerFactory);
    }

    public class ConfigurationResolvableDependencies implements ResolvableDependenciesInternal {

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getPath() {
            // TODO: Can we update this to identityPath?
            return projectPath.getPath();
        }

        @Override
        public String toString() {
            return "dependencies '" + identityPath + "'";
        }

        @Override
        public FileCollection getFiles() {
            assertIsResolvable();
            return intrinsicFiles;
        }

        @Override
        public DependencySet getDependencies() {
            runDependencyActions();
            return getAllDependencies();
        }

        @Override
        public DependencyConstraintSet getDependencyConstraints() {
            runDependencyActions();
            return getAllDependencyConstraints();
        }

        @Override
        public void beforeResolve(Action<? super ResolvableDependencies> action) {
            dependencyResolutionListeners.add("beforeResolve", userCodeApplicationContext.reapplyCurrentLater(action));
        }

        @Override
        public void beforeResolve(Closure action) {
            beforeResolve(ConfigureUtil.configureUsing(action));
        }

        @Override
        public void afterResolve(Action<? super ResolvableDependencies> action) {
            dependencyResolutionListeners.add("afterResolve", userCodeApplicationContext.reapplyCurrentLater(action));
        }

        @Override
        public void afterResolve(Closure action) {
            afterResolve(ConfigureUtil.configureUsing(action));
        }

        @Override
        public ResolutionResult getResolutionResult() {
            assertIsResolvable();
            return new DefaultResolutionResult(new ConfigurationResolvingMinimalResolutionResult());
        }

        @Override
        public ArtifactCollection getArtifacts() {
            return artifactCollection(configurationAttributes, Specs.satisfyAll(), false, false, false);
        }

        @Override
        public ArtifactView artifactView(Action<? super ArtifactView.ViewConfiguration> configAction) {
            ArtifactViewConfiguration config = createArtifactViewConfiguration();
            configAction.execute(config);
            return createArtifactView(config);
        }

        private ArtifactView createArtifactView(ArtifactViewConfiguration config) {
            // As this runs after the action used to configure the ArtifactView has run, the config might already have viewAttributes present
            // which the user added.  If so, continue to use those attributes, otherwise use the attributes from the creating configuration.
            AttributeContainerInternal viewAttributes = (config.viewAttributes == null) ? config.configurationAttributes : config.viewAttributes;

            // This is a little coincidental: if view attributes have not been accessed, don't allow no matching variants
            boolean allowNoMatchingVariants = config.attributesUsed;

            return new ConfigurationArtifactView(viewAttributes, config.lockComponentFilter(), config.lenient, allowNoMatchingVariants, config.reselectVariant);
        }

        private DefaultConfiguration.ArtifactViewConfiguration createArtifactViewConfiguration() {
            return instantiator.newInstance(ArtifactViewConfiguration.class, attributesFactory, configurationAttributes);
        }

        @Override
        public AttributeContainer getAttributes() {
            return configurationAttributes;
        }

        @Override
        public ResolutionResultProvider<VisitedGraphResults> getGraphResultsProvider() {
            assertIsResolvable();
            return new ResolutionResultProvider<VisitedGraphResults>() {
                @Override
                public VisitedGraphResults getTaskDependencyValue() {
                    return resolveGraphForBuildDependenciesIfRequired().getVisitedGraph();
                }

                @Override
                public VisitedGraphResults getValue() {
                    return resolveToStateOrLater(ARTIFACTS_RESOLVED).getCachedResolverResults().getVisitedGraph();
                }
            };
        }

        private class ConfigurationArtifactView implements ArtifactView {
            private final AttributeContainerInternal viewAttributes;
            private final Spec<? super ComponentIdentifier> componentFilter;
            private final boolean lenient;
            private final boolean allowNoMatchingVariants;
            private final boolean selectFromAllVariants;

            ConfigurationArtifactView(AttributeContainerInternal viewAttributes, Spec<? super ComponentIdentifier> componentFilter, boolean lenient, boolean allowNoMatchingVariants, boolean selectFromAllVariants) {
                this.viewAttributes = viewAttributes;
                this.componentFilter = componentFilter;
                this.lenient = lenient;
                this.allowNoMatchingVariants = allowNoMatchingVariants;
                this.selectFromAllVariants = selectFromAllVariants;
            }

            @Override
            public AttributeContainer getAttributes() {
                return viewAttributes.asImmutable();
            }

            @Override
            public ArtifactCollection getArtifacts() {
                return artifactCollection(viewAttributes, componentFilter, lenient, allowNoMatchingVariants, selectFromAllVariants);
            }

            @Override
            public FileCollection getFiles() {
                // TODO maybe make detached configuration is flag is true
                return new ResolutionBackedFileCollection(
                    new SelectedArtifactsProvider(Specs.satisfyAll(), viewAttributes, componentFilter, allowNoMatchingVariants, selectFromAllVariants, new VisitedArtifactsSetProvider()),
                    lenient,
                    new DefaultResolutionHost(),
                    taskDependencyFactory
                );
            }
        }

        /**
         * A minimal resolution result that lazily resolves the configuration.
         */
        private class ConfigurationResolvingMinimalResolutionResult implements MinimalResolutionResult {

            private MinimalResolutionResult getDelegate() {
                VisitedGraphResults graph = getGraphResultsProvider().getValue();
                graph.getResolutionFailure().ifPresent(ex -> {
                    throw ex;
                });
                return graph.getResolutionResult();
            }

            @Override
            public Supplier<ResolvedComponentResult> getRootSource() {
                return getDelegate().getRootSource();
            }

            @Override
            public ImmutableAttributes getRequestedAttributes() {
                return getDelegate().getRequestedAttributes();
            }

            @Override
            public int hashCode() {
                return getDelegate().hashCode();
            }

            @Override
            public boolean equals(Object obj) {
                if (obj instanceof ConfigurationResolvingMinimalResolutionResult) {
                    return getDelegate().equals(((ConfigurationResolvingMinimalResolutionResult) obj).getDelegate());
                }
                return false;
            }
        }
    }

    public static class ArtifactViewConfiguration implements ArtifactView.ViewConfiguration {
        private final ImmutableAttributesFactory attributesFactory;
        private final AttributeContainerInternal configurationAttributes;
        private AttributeContainerInternal viewAttributes;
        private Spec<? super ComponentIdentifier> componentFilter;
        private boolean lenient;
        private boolean reselectVariant;
        private boolean attributesUsed;

        public ArtifactViewConfiguration(ImmutableAttributesFactory attributesFactory, AttributeContainerInternal configurationAttributes) {
            this.attributesFactory = attributesFactory;
            this.configurationAttributes = configurationAttributes;
        }

        @Override
        public AttributeContainer getAttributes() {
            if (viewAttributes == null) {
                if (reselectVariant) {
                    viewAttributes = attributesFactory.mutable();
                } else {
                    viewAttributes = attributesFactory.mutable(configurationAttributes);
                }
                attributesUsed = true;
            }
            return viewAttributes;
        }

        @Override
        public ArtifactViewConfiguration attributes(Action<? super AttributeContainer> action) {
            action.execute(getAttributes());
            return this;
        }

        @Override
        public ArtifactViewConfiguration componentFilter(Spec<? super ComponentIdentifier> componentFilter) {
            assertComponentFilterUnset();
            this.componentFilter = componentFilter;
            return this;
        }

        @Override
        public boolean isLenient() {
            return lenient;
        }

        @Override
        public void setLenient(boolean lenient) {
            this.lenient = lenient;
        }

        @Override
        public ArtifactViewConfiguration lenient(boolean lenient) {
            this.lenient = lenient;
            return this;
        }

        @Override
        public ArtifactViewConfiguration withVariantReselection() {
            this.reselectVariant = true;
            return this;
        }

        private void assertComponentFilterUnset() {
            if (componentFilter != null) {
                throw new IllegalStateException("The component filter can only be set once before the view was computed");
            }
        }

        private Spec<? super ComponentIdentifier> lockComponentFilter() {
            if (componentFilter == null) {
                componentFilter = Specs.satisfyAll();
            }
            return componentFilter;
        }
    }

    private class AllArtifactsProvider implements PublishArtifactSetProvider {
        @Override
        public PublishArtifactSet getPublishArtifactSet() {
            return getAllArtifacts();
        }
    }

    private class DefaultResolutionHost implements ResolutionHost {
        @Override
        public String getDisplayName() {
            return DefaultConfiguration.this.getDisplayName();
        }

        @Override
        public DisplayName displayName(String type) {
            return Describables.of(DefaultConfiguration.this, type);
        }

        @Override
        public Optional<? extends RuntimeException> mapFailure(String type, Collection<Throwable> failures) {
            return Optional.ofNullable(exceptionContextualizer.mapFailures(failures, DefaultConfiguration.this.getDisplayName(), type));
        }
    }

    private enum ProperMethodUsage {
        CONSUMABLE {
            @Override
            boolean isAllowed(ConfigurationInternal configuration) {
                return configuration.isCanBeConsumed();
            }

            @Override
            boolean isDeprecated(ConfigurationInternal configuration) {
                return configuration.isDeprecatedForConsumption();
            }
        },
        RESOLVABLE {
            @Override
            boolean isAllowed(ConfigurationInternal configuration) {
                return configuration.isCanBeResolved();
            }

            @Override
            boolean isDeprecated(ConfigurationInternal configuration) {
                return configuration.isDeprecatedForResolution();
            }
        },
        DECLARABLE_AGAINST {
            @Override
            boolean isAllowed(ConfigurationInternal configuration) {
                return configuration.isCanBeDeclared();
            }

            @Override
            boolean isDeprecated(ConfigurationInternal configuration) {
                return configuration.isDeprecatedForDeclarationAgainst();
            }
        };

        abstract boolean isAllowed(ConfigurationInternal configuration);

        abstract boolean isDeprecated(ConfigurationInternal configuration);

        boolean isProperUsage(ConfigurationInternal configuration, boolean allowDeprecated) {
            return isAllowed(configuration) && (allowDeprecated || !isDeprecated(configuration));
        }

        public static String buildProperName(ProperMethodUsage usage) {
            return WordUtils.capitalizeFully(usage.name().replace('_', ' '));
        }

        public static String summarizeProperUsage(ProperMethodUsage... properUsages) {
            return Arrays.stream(properUsages)
                    .map(ProperMethodUsage::buildProperName)
                    .collect(Collectors.joining(", "));
        }
    }
}
