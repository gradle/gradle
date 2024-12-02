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
import groovy.lang.Closure;
import org.apache.commons.lang.WordUtils;
import org.gradle.api.Action;
import org.gradle.api.Describable;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.InvalidUserDataException;
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
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.PublishArtifactSet;
import org.gradle.api.artifacts.ResolutionStrategy;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.VersionConstraint;
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
import org.gradle.api.internal.artifacts.ResolveExceptionMapper;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyConstraint;
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint;
import org.gradle.api.internal.artifacts.dependencies.DependencyConstraintInternal;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingState;
import org.gradle.api.internal.artifacts.ivyservice.TypedResolveException;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.RootComponentMetadataBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.Conflict;
import org.gradle.api.internal.artifacts.resolver.DefaultResolutionOutputs;
import org.gradle.api.internal.artifacts.resolver.ResolutionAccess;
import org.gradle.api.internal.artifacts.resolver.ResolutionOutputsInternal;
import org.gradle.api.internal.artifacts.result.DefaultResolutionResult;
import org.gradle.api.internal.artifacts.result.MinimalResolutionResult;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.attributes.FreezableAttributeContainer;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.api.internal.initialization.ResettableConfiguration;
import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.problems.internal.InternalProblems;
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
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.LocalComponentDependencyMetadata;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.model.CalculatedModelValue;
import org.gradle.internal.model.CalculatedValue;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.scopes.DetachedDependencyMetadataProvider;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.work.WorkerThreadRegistry;
import org.gradle.operations.dependencies.configurations.ConfigurationIdentity;
import org.gradle.util.Path;
import org.gradle.util.internal.CollectionUtils;
import org.gradle.util.internal.ConfigureUtil;
import org.gradle.util.internal.WrapUtil;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.gradle.api.internal.artifacts.configurations.ConfigurationInternal.InternalState.GRAPH_RESOLVED;
import static org.gradle.api.internal.artifacts.configurations.ConfigurationInternal.InternalState.UNRESOLVED;
import static org.gradle.util.internal.ConfigureUtil.configure;

/**
 * The default {@link Configuration} implementation.
 */
@SuppressWarnings("rawtypes")
public abstract class DefaultConfiguration extends AbstractFileCollection implements ConfigurationInternal, MutationValidator, ResettableConfiguration {
    private final ConfigurationResolver resolver;
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
    private final BuildOperationRunner buildOperationRunner;
    private final Instantiator instantiator;
    private Factory<ResolutionStrategyInternal> resolutionStrategyFactory;
    private ResolutionStrategyInternal resolutionStrategy;
    private final FileCollectionFactory fileCollectionFactory;
    private final ResolveExceptionMapper exceptionMapper;
    private final AttributeDesugaring attributeDesugaring;

    private final Set<MutationValidator> childMutationValidators = new HashSet<>();
    private final MutationValidator parentMutationValidator = DefaultConfiguration.this::validateParentMutation;
    private final RootComponentMetadataBuilder rootComponentMetadataBuilder;
    private RootComponentMetadataBuilder.RootComponentState rootComponentState;
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

    private boolean canBeConsumed;
    private boolean canBeResolved;
    private boolean canBeDeclaredAgainst;
    private final boolean consumptionDeprecated;
    private final boolean resolutionDeprecated;
    private final boolean declarationDeprecated;
    private boolean usageCanBeMutated = true;
    private final ConfigurationRole roleAtCreation;

    private boolean observed = false;
    private final FreezableAttributeContainer configurationAttributes;
    private final DomainObjectContext domainObjectContext;
    private final AttributesFactory attributesFactory;
    private final ResolutionAccess resolutionAccess;
    private FileCollectionInternal intrinsicFiles;

    private final DisplayName displayName;
    private final UserCodeApplicationContext userCodeApplicationContext;
    private final WorkerThreadRegistry workerThreadRegistry;
    private final DomainObjectCollectionFactory domainObjectCollectionFactory;

    private final AtomicInteger copyCount = new AtomicInteger();

    private List<String> declarationAlternatives = ImmutableList.of();
    private List<String> resolutionAlternatives = ImmutableList.of();

    private final CalculatedModelValue<Optional<ResolverResults>> currentResolveState;

    private ConfigurationInternal consistentResolutionSource;
    private String consistentResolutionReason;
    private final DefaultConfigurationFactory defaultConfigurationFactory;
    private final InternalProblems problemsService;

    /**
     * To create an instance, use {@link DefaultConfigurationFactory#create}.
     */
    public DefaultConfiguration(
        DomainObjectContext domainObjectContext,
        String name,
        ConfigurationsProvider configurationsProvider,
        ConfigurationResolver resolver,
        ListenerBroadcast<DependencyResolutionListener> dependencyResolutionListeners,
        DependencyLockingProvider dependencyLockingProvider,
        Factory<ResolutionStrategyInternal> resolutionStrategyFactory,
        FileCollectionFactory fileCollectionFactory,
        BuildOperationRunner buildOperationRunner,
        Instantiator instantiator,
        NotationParser<Object, ConfigurablePublishArtifact> artifactNotationParser,
        NotationParser<Object, Capability> capabilityNotationParser,
        AttributesFactory attributesFactory,
        RootComponentMetadataBuilder rootComponentMetadataBuilder,
        ResolveExceptionMapper exceptionMapper,
        AttributeDesugaring attributeDesugaring,
        UserCodeApplicationContext userCodeApplicationContext,
        ProjectStateRegistry projectStateRegistry,
        WorkerThreadRegistry workerThreadRegistry,
        DomainObjectCollectionFactory domainObjectCollectionFactory,
        CalculatedValueContainerFactory calculatedValueContainerFactory,
        DefaultConfigurationFactory defaultConfigurationFactory,
        TaskDependencyFactory taskDependencyFactory,
        ConfigurationRole roleAtCreation,
        InternalProblems problemsService,
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
        this.dependencyLockingProvider = dependencyLockingProvider;
        this.resolutionStrategyFactory = resolutionStrategyFactory;
        this.fileCollectionFactory = fileCollectionFactory;
        this.dependencyResolutionListeners = dependencyResolutionListeners;
        this.buildOperationRunner = buildOperationRunner;
        this.instantiator = instantiator;
        this.attributesFactory = attributesFactory;
        this.domainObjectContext = domainObjectContext;
        this.exceptionMapper = exceptionMapper;
        this.attributeDesugaring = attributeDesugaring;

        this.displayName = Describables.memoize(new ConfigurationDescription(identityPath));
        this.configurationAttributes = new FreezableAttributeContainer(attributesFactory.mutable(), this.displayName);

        this.resolutionAccess = new ConfigurationResolutionAccess();
        this.resolvableDependencies = instantiator.newInstance(ConfigurationResolvableDependencies.class, this);

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
        this.currentResolveState = domainObjectContext.getModel().newCalculatedValue(Optional.empty());
        this.defaultConfigurationFactory = defaultConfigurationFactory;
        this.problemsService = problemsService;

        this.canBeConsumed = roleAtCreation.isConsumable();
        this.canBeResolved = roleAtCreation.isResolvable();
        this.canBeDeclaredAgainst = roleAtCreation.isDeclarable();
        this.consumptionDeprecated = roleAtCreation.isConsumptionDeprecated();
        this.resolutionDeprecated = roleAtCreation.isResolutionDeprecated();
        this.declarationDeprecated = roleAtCreation.isDeclarationAgainstDeprecated();
        this.usageCanBeMutated = !lockUsage;
        this.roleAtCreation = roleAtCreation;
    }

    private static Action<String> validateMutationType(final MutationValidator mutationValidator, final MutationType type) {
        return arg -> mutationValidator.validateMutation(type);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public State getState() {
        Optional<ResolverResults> currentState = currentResolveState.get();
        if (!currentState.isPresent()) {
            return State.UNRESOLVED;
        }

        ResolverResults resolvedState = currentState.get();
        if (resolvedState.getVisitedGraph().hasAnyFailure()) {
            return State.RESOLVED_WITH_FAILURES;
        } else if (resolvedState.isFullyResolved()) {
            return State.RESOLVED;
        } else {
            return State.UNRESOLVED;
        }
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
        assertNotDetachedExtensionDoingExtending(extendsFrom);
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
        assertNotDetachedExtensionDoingExtending(Arrays.asList(extendsFrom));
        for (Configuration extended : extendsFrom) {
            ConfigurationInternal other = Objects.requireNonNull(Cast.uncheckedCast(extended));
            if (!domainObjectContext.equals(other.getDomainObjectContext())) {

                String message = String.format(
                    "Configuration '%s' in %s extends configuration '%s' in %s.",
                    this.getName(),
                    this.domainObjectContext.getDisplayName(),
                    other.getName(),
                    other.getDomainObjectContext().getDisplayName()
                );

                DeprecationLogger.deprecateBehaviour(message)
                    .withAdvice("Configurations can only extend from configurations in the same project.")
                    .willBeRemovedInGradle9()
                    .withUpgradeGuideSection(8, "extending_configurations_in_same_project")
                    .nagUser();

            }
            if (other.getHierarchy().contains(this)) {
                throw new InvalidUserDataException(String.format(
                    "Cyclic extendsFrom from %s and %s is not allowed. See existing hierarchy: %s", this,
                    other, other.getHierarchy()));
            }
            if (this.extendsFrom.add(other)) {
                if (inheritedArtifacts != null) {
                    inheritedArtifacts.addCollection(other.getAllArtifacts());
                }
                if (inheritedDependencies != null) {
                    inheritedDependencies.addCollection(other.getAllDependencies());
                }
                if (inheritedDependencyConstraints != null) {
                    inheritedDependencyConstraints.addCollection(other.getAllDependencyConstraints());
                }
                other.addMutationValidator(parentMutationValidator);
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
        runActionInHierarchy(conf -> {
            conf.defaultDependencyActions.execute(conf.dependencies);
            conf.withDependencyActions.execute(conf.dependencies);

            // Discard actions after execution
            conf.defaultDependencyActions = ImmutableActionSet.empty();
            conf.withDependencyActions = ImmutableActionSet.empty();
        });
    }

    @Deprecated
    @Override
    public Set<Configuration> getAll() {
        DeprecationLogger.deprecateMethod(Configuration.class, "getAll()")
            .withAdvice("Use the configurations container to access the set of configurations instead.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "deprecated_configuration_get_all")
            .nagUser();

        return ImmutableSet.copyOf(configurationsProvider.getAll());
    }

    private FileCollectionInternal getIntrinsicFiles() {
        if (intrinsicFiles == null) {
            assertIsResolvable();
            intrinsicFiles = resolutionAccess.getPublicView().getFiles();
        }
        return intrinsicFiles;
    }

    @Override
    public Set<File> resolve() {
        warnOnDeprecatedUsage("resolve()", ProperMethodUsage.RESOLVABLE);
        return getFiles();
    }

    @Override
    public Iterator<File> iterator() {
        return getIntrinsicFiles().iterator();
    }

    @Override
    protected void visitContents(FileCollectionStructureVisitor visitor) {
        getIntrinsicFiles().visitStructure(visitor);
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
        return getIntrinsicFiles().contains(file);
    }

    @Override
    public boolean isEmpty() {
        return getIntrinsicFiles().isEmpty();
    }

    @Override
    @Deprecated
    public Set<File> files(Dependency... dependencies) {
        Set<Dependency> deps = WrapUtil.toLinkedSet(dependencies);
        return fileCollectionInternal("files(Dependency...)", deps::contains).getFiles();
    }

    @Override
    @Deprecated
    public Set<File> files(Closure dependencySpecClosure) {
        return fileCollectionInternal("files(Closure)", Specs.convertClosureToSpec(dependencySpecClosure)).getFiles();
    }

    @Override
    @Deprecated
    public Set<File> files(Spec<? super Dependency> dependencySpec) {
        return fileCollectionInternal("files(Spec)", dependencySpec).getFiles();
    }

    @Override
    @Deprecated
    public FileCollection fileCollection(Closure dependencySpecClosure) {
        return fileCollectionInternal("fileCollection(Closure)", Specs.convertClosureToSpec(dependencySpecClosure));
    }

    @Override
    @Deprecated
    public FileCollection fileCollection(Dependency... dependencies) {
        Set<Dependency> deps = WrapUtil.toLinkedSet(dependencies);
        return fileCollectionInternal("fileCollection(Dependency...)", deps::contains);
    }

    @Override
    @Deprecated
    public FileCollection fileCollection(Spec<? super Dependency> dependencySpec) {
        return fileCollectionInternal("fileCollection(Spec)", dependencySpec);
    }

    private FileCollection fileCollectionInternal(String methodName, Spec<? super Dependency> dependencySpec) {
        assertIsResolvable();

        DeprecationLogger.deprecateMethod(Configuration.class, methodName)
            .withAdvice("Use Configuration.getIncoming().artifactView(Action) with a componentFilter instead.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "deprecate_filtered_configuration_file_and_filecollection_methods")
            .nagUser();

        return new ResolutionBackedFileCollection(
            new ResolutionResultProviderBackedSelectedArtifactSet(
                resolutionAccess.getResults().map(resolverResults ->
                    resolverResults.getLegacyResults().getLegacyVisitedArtifactSet().select(dependencySpec)
                )
            ),
            false,
            getResolutionHost(),
            taskDependencyFactory
        );
    }

    @Override
    public void markAsObserved(InternalState requestedState) {
        synchronized (observationLock) {
            if (observedState.compareTo(requestedState) < 0) {
                observedState = requestedState;
            } else {
                // If the target state is the same as or greater than the current state,
                // we and our parents are already at this state or later and we can skip.
                return;
            }
        }
        markParentsObserved(requestedState);
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
        return resolutionAccess.getResults().getValue().getLegacyResults().getResolvedConfiguration();
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static Boolean isFullyResoled(Optional<ResolverResults> currentState) {
        return currentState.map(ResolverResults::isFullyResolved).orElse(false);
    }

    private class ConfigurationResolutionAccess implements ResolutionAccess {

        @Override
        public ResolutionHost getHost() {
            return new DefaultResolutionHost(DefaultConfiguration.this);
        }

        @Override
        public ImmutableAttributes getAttributes() {
            configurationAttributes.freeze();
            return configurationAttributes.asImmutable();
        }

        @Override
        public ResolutionStrategy.SortOrder getDefaultSortOrder() {
            return getResolutionStrategy().getSortOrder();
        }

        @Override
        public ResolutionResultProvider<ResolverResults> getResults() {
            return new ResolverResultsResolutionResultProvider();
        }

        @Override
        public ResolutionOutputsInternal getPublicView() {
            return new DefaultResolutionOutputs(
                this,
                taskDependencyFactory,
                calculatedValueContainerFactory,
                attributesFactory,
                instantiator
            );
        }
    }

    /**
     * A provider that lazily resolves this configuration.
     */
    private class ResolverResultsResolutionResultProvider implements ResolutionResultProvider<ResolverResults> {

        @Override
        public ResolverResults getTaskDependencyValue() {
            if (getResolutionStrategy().resolveGraphToDetermineTaskDependencies()) {
                // Force graph resolution as this is required to calculate build dependencies
                return getValue();
            } else {
                return resolveGraphForBuildDependenciesIfRequired();
            }
        }

        @Override
        public ResolverResults getValue() {
            return resolveGraphIfRequired();
        }

    }

    private ResolverResults resolveGraphIfRequired() {
        assertIsResolvable();
        maybeEmitResolutionDeprecation();

        Optional<ResolverResults> currentState = currentResolveState.get();
        if (isFullyResoled(currentState)) {
            return currentState.get();
        }

        ResolverResults newState;
        if (!domainObjectContext.getModel().hasMutableState()) {
            if (!workerThreadRegistry.isWorkerThread()) {
                // Error if we are executing in a user-managed thread.
                throw new IllegalStateException("The configuration " + identityPath.toString() + " was resolved from a thread not managed by Gradle.");
            } else {
                DeprecationLogger.deprecateBehaviour("Resolution of the configuration " + identityPath.toString() + " was attempted from a context different than the project context. Have a look at the documentation to understand why this is a problem and how it can be resolved.")
                    .willBecomeAnErrorInGradle9()
                    .withUserManual("viewing_debugging_dependencies", "sub:resolving-unsafe-configuration-resolution-errors")
                    .nagUser();
                newState = domainObjectContext.getModel().fromMutableState(p -> resolveExclusivelyIfRequired());
            }
        } else {
            newState = resolveExclusivelyIfRequired();
        }

        return newState;
    }

    private ResolverResults resolveExclusivelyIfRequired() {
        return currentResolveState.update(currentState -> {
            if (isFullyResoled(currentState)) {
                return currentState;
            }

            return Optional.of(resolveGraphInBuildOperation());
        }).get();
    }

    /**
     * Must be called from {@link #resolveExclusivelyIfRequired} only.
     */
    private ResolverResults resolveGraphInBuildOperation() {
        return buildOperationRunner.call(new CallableBuildOperation<ResolverResults>() {
            @Override
            public ResolverResults call(BuildOperationContext context) {
                runDependencyActions();
                runBeforeResolve();

                ResolverResults results;
                try {
                    results = resolver.resolveGraph(DefaultConfiguration.this);
                    DefaultConfiguration.this.rootComponentState = null;
                } catch (Exception e) {
                    throw exceptionMapper.mapFailure(e, "dependencies", displayName.getDisplayName());
                }

                // Make the new state visible in case a dependency resolution listener queries the result, which requires the new state
                currentResolveState.set(Optional.of(results));

                // Mark all affected configurations as observed
                markParentsObserved(GRAPH_RESOLVED);

                // TODO: Currently afterResolve runs if there are unresolved dependencies, which are
                //       resolution failures. However, they are not run for other failures.
                //       We should either _always_ run afterResolve, or only run it if _no_ failure occurred
                if (!results.getVisitedGraph().getResolutionFailure().isPresent()) {
                    dependencyResolutionListeners.getSource().afterResolve(getIncoming());
                }

                // Discard State
                dependencyResolutionListeners.removeAll();
                if (resolutionStrategy != null) {
                    resolutionStrategy.maybeDiscardStateRequiredForGraphResolution();
                }

                captureBuildOperationResult(context, results);
                return results;
            }

            private void captureBuildOperationResult(BuildOperationContext context, ResolverResults results) {
                results.getVisitedGraph().getResolutionFailure().ifPresent(context::failed);
                // When dependency resolution has failed, we don't want the build operation listeners to fail as well
                // because:
                // 1. the `failed` method will have been called with the user facing error
                // 2. such an error may still lead to a valid dependency graph
                MinimalResolutionResult resolutionResult = results.getVisitedGraph().getResolutionResult();
                context.setResult(new ResolveConfigurationResolutionBuildOperationResult(
                    resolutionResult.getRootSource(),
                    resolutionResult.getRequestedAttributes(),
                    attributesFactory
                ));
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                String displayName = "Resolve dependencies of " + identityPath;
                ProjectIdentity projectId = domainObjectContext.getProjectIdentity();
                String projectPathString = null;
                if (!domainObjectContext.isScript()) {
                    if (projectId != null) {
                        projectPathString = projectId.getProjectPath().getPath();
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
                        resolver.getAllRepositories()
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
        Set<ConfigurationInternal> sources = new LinkedHashSet<>();
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

    /**
     * Run the {@link ResolvableDependencies#beforeResolve(Action)} hook.
     */
    private void runBeforeResolve() {
        DependencyResolutionListener dependencyResolutionListener = dependencyResolutionListeners.getSource();
        insideBeforeResolve = true;
        try {
            dependencyResolutionListener.beforeResolve(getIncoming());
        } finally {
            insideBeforeResolve = false;
        }
    }

    @Override
    public <T> T callAndResetResolutionState(Factory<T> factory) {
        warnOnInvalidInternalAPIUsage("callAndResetResolutionState()", ProperMethodUsage.RESOLVABLE);
        try {
            // Prevent the state required for resolution from being discarded if anything in the
            // factory resolves this configuration
            getResolutionStrategy().setKeepStateRequiredForGraphResolution(true);

            T value = factory.create();

            // Reset this configuration to an unresolved state
            currentResolveState.set(Optional.empty());
            rootComponentState = null;

            return value;
        } finally {
            getResolutionStrategy().setKeepStateRequiredForGraphResolution(false);
        }
    }

    private ResolverResults resolveGraphForBuildDependenciesIfRequired() {
        assertIsResolvable();
        return currentResolveState.update(initial -> {
            if (!initial.isPresent()) {

                CalculatedValue<ResolverResults> futureCompleteResults = calculatedValueContainerFactory.create(Describables.of("Full results for", getName()), context -> {
                    Optional<ResolverResults> currentState = currentResolveState.get();
                    if (!isFullyResoled(currentState)) {
                        // Do not validate that the current thread holds the project lock.
                        // TODO: Should instead assert that the results are available and fail if not.
                        return resolveExclusivelyIfRequired();
                    }
                    return currentState.get();
                });

                try {
                    return Optional.of(resolver.resolveBuildDependencies(this, futureCompleteResults));
                } catch (Exception e) {
                    throw exceptionMapper.mapFailure(e, "dependencies", displayName.getDisplayName());
                }
            } // Otherwise, already have a result, so reuse it
            return initial;
        }).get();
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        context.add(getIntrinsicFiles());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskDependency getTaskDependencyFromProjectDependency(final boolean useDependedOn, final String taskName) {
        if (useDependedOn) {
            return new TasksFromProjectDependencies(taskName, () -> {
                return getAllDependencies().withType(ProjectDependency.class);
            }, taskDependencyFactory, projectStateRegistry);
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

        if (observed && extendsFrom.isEmpty()) {
            // No further mutation is allowed and there's no parent: the artifact set corresponds to this configuration own artifacts
            this.allArtifacts = new DefaultPublishArtifactSet(displayName, ownArtifacts, fileCollectionFactory, taskDependencyFactory);
            return;
        }

        if (!observed) {
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
        Set<ExcludeRule> result = new LinkedHashSet<>();
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
            parsedExcludeRules = new LinkedHashSet<>();
            for (Object excludeRule : excludeRules) {
                parsedExcludeRules.add(parser.parseNotation(excludeRule));
            }
        }
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
    public String getDisplayName() {
        return displayName.getDisplayName();
    }

    @Override
    public DisplayName asDescribable() {
        return displayName;
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
        boolean immutable = observed || currentResolveState.get().isPresent();
        return !immutable;
    }

    @Override
    public void markAsObserved() {
        if (observed) {
            return;
        }

        runActionInHierarchy(conf -> {
            if (!conf.observed) {
                conf.configurationAttributes.freeze();
                conf.outgoing.preventFromFurtherMutation();
                conf.preventUsageMutation();
                conf.observed = true;
            }
        });
    }

    /**
     * Runs the provided action for this configuration and all configurations that it extends from.
     *
     * <p>Specifically handles the case where {@link Configuration#extendsFrom} is called during the
     * action execution.</p>
     */
    private void runActionInHierarchy(Action<DefaultConfiguration> action) {
        Set<Configuration> seen = new HashSet<>();
        Queue<Configuration> remaining = new ArrayDeque<>();
        remaining.add(this);

        while (!remaining.isEmpty()) {
            Configuration current = remaining.remove();
            action.execute((DefaultConfiguration) current);

            for (Configuration parent : current.getExtendsFrom()) {
                if (seen.add(parent)) {
                    remaining.add(parent);
                }
            }
        }
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
        DefaultConfiguration copiedConfiguration = copyAsDetached();

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

    private DefaultConfiguration copyAsDetached() {
        String newName = getNameWithCopySuffix();
        DetachedConfigurationsProvider configurationsProvider = new DetachedConfigurationsProvider();

        DependencyMetaDataProvider componentIdentity = new DetachedDependencyMetadataProvider(rootComponentMetadataBuilder.getComponentIdentity());
        RootComponentMetadataBuilder rootComponentMetadataBuilder = this.rootComponentMetadataBuilder.newBuilder(componentIdentity, configurationsProvider);

        Factory<ResolutionStrategyInternal> childResolutionStrategy = resolutionStrategy != null ? Factories.constant(resolutionStrategy.copy()) : resolutionStrategyFactory;

        @SuppressWarnings("deprecation")
        DefaultConfiguration copiedConfiguration = defaultConfigurationFactory.create(
            newName,
            configurationsProvider,
            childResolutionStrategy,
            rootComponentMetadataBuilder,
            ConfigurationRolesForMigration.LEGACY_TO_RESOLVABLE_DEPENDENCY_SCOPE
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
        if (rootComponentState == null) {
            rootComponentState = rootComponentMetadataBuilder.toRootComponent(getName());
        }
        return rootComponentState;
    }

    @Override
    public String getDependencyLockingId() {
        return name;
    }

    @Override
    public List<? extends DependencyMetadata> getSyntheticDependencies() {
        warnOnInvalidInternalAPIUsage("getSyntheticDependencies()", ProperMethodUsage.RESOLVABLE);
        Stream<LocalComponentDependencyMetadata> dependencyLockingConstraintMetadata = Stream.empty();
        if (getResolutionStrategy().isDependencyLockingEnabled()) {
            DependencyLockingState dependencyLockingState = dependencyLockingProvider.loadLockState(getDependencyLockingId(), displayName);
            boolean strict = dependencyLockingState.mustValidateLockState();
            dependencyLockingConstraintMetadata = dependencyLockingState.getLockedDependencies().stream().map(lockedDependency -> {
                String lockedVersion = lockedDependency.getVersion();
                VersionConstraint versionConstraint = strict
                    ? DefaultMutableVersionConstraint.withStrictVersion(lockedVersion)
                    : DefaultMutableVersionConstraint.withVersion(lockedVersion);
                ModuleComponentSelector selector = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(lockedDependency.getGroup(), lockedDependency.getModule()), versionConstraint);
                return new LocalComponentDependencyMetadata(
                    selector, null, Collections.emptyList(), Collections.emptyList(),
                    false, false, false, true, false, true, getLockReason(strict, lockedVersion)
                );
            });
        }

        Stream<LocalComponentDependencyMetadata> consistentResolutionConstraintMetadata = getConsistentResolutionConstraints().map(dc -> {
            ModuleComponentSelector selector = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(dc.getGroup(), dc.getName()), dc.getVersionConstraint());
            return new LocalComponentDependencyMetadata(
                selector, null, Collections.emptyList(), Collections.emptyList(),
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

    /**
     * Called when a parent configuration is mutated.
     */
    private void validateParentMutation(MutationType type) {
        // Strategy changes in a parent configuration do not affect this configuration, or any of its children, in any way
        if (type == MutationType.STRATEGY) {
            return;
        }

        preventIllegalParentMutation(type);
        boolean emittedDeprecation = maybePreventMutation(type, type + " of parent");

        // Notify children of this mutation, but don't emit a deprecation if we already emitted one
        // at this level, otherwise we spam for no reason. We can remove this once the deprecation
        // turns into an error, since the error will short-circuit the child notifications.
        if (emittedDeprecation) {
            DeprecationLogger.whileDisabled(() -> notifyChildren(type));
        } else {
            notifyChildren(type);
        }
    }

    @Override
    public void validateMutation(MutationType type) {
        preventIllegalMutation(type);
        boolean emittedDeprecation = maybePreventMutation(type, type.toString());

        // Notify children of this mutation, but don't emit a deprecation if we already emitted one
        // at this level, otherwise we spam for no reason. We can remove this once the deprecation
        // turns into an error, since the error will short-circuit the child notifications.
        if (emittedDeprecation) {
            DeprecationLogger.whileDisabled(() -> notifyChildren(type));
        } else {
            notifyChildren(type);
        }
    }

    /**
     * Emit a warning (and eventually throw an exception) if a mutation of type {@code type} occurs
     * during a forbidden state.
     *
     * @return true if a deprecation was emitted
     */
    private boolean maybePreventMutation(MutationType type, String typeDescription) {
        // If an external party has seen the public state (variant metadata) of our configuration,
        // we forbid any mutation that mutates the public state. The resolution strategy does
        // not mutate the public state of the configuration, so we allow it.
        if (observed && type != MutationType.STRATEGY) {
            DeprecationLogger.deprecateBehaviour(String.format("Mutating the %s of %s after it has been resolved or consumed.", typeDescription, this.getDisplayName()))
                .withAdvice("After a Configuration has been resolved, consumed as a variant, or used for generating published metadata, it should not be modified.")
                .willBecomeAnErrorInGradle9()
                .withUpgradeGuideSection(8, "mutate_configuration_after_locking")
                .nagUser();
            return true;
        }
        return false;
    }

    private void preventIllegalParentMutation(MutationType type) {
        // TODO: We can remove this check once we turn `maybePreventMutation` into an error
        if (type == MutationType.DEPENDENCY_ATTRIBUTES || type == MutationType.DEPENDENCY_CONSTRAINT_ATTRIBUTES) {
            return;
        }

        if (isFullyResoled(currentResolveState.get())) {
            throw new InvalidUserDataException(String.format("Cannot change %s of parent of %s after it has been resolved", type, getDisplayName()));
        }
    }

    private void preventIllegalMutation(MutationType type) {
        // TODO: We can remove this check once we turn `maybePreventMutation` into an error
        if (type == MutationType.DEPENDENCY_ATTRIBUTES || type == MutationType.DEPENDENCY_CONSTRAINT_ATTRIBUTES) {
            assertIsDeclarable("Changing " + type);
            return;
        }

        if (isFullyResoled(currentResolveState.get())) {
            // The public result for the configuration has been calculated.
            // It is an error to change anything that would change the dependencies or artifacts
            throw new InvalidUserDataException(String.format("Cannot change %s of dependency %s after it has been resolved.", type, getDisplayName()));
        } else if (observedState == GRAPH_RESOLVED) {
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

    private void notifyChildren(MutationType type) {
        // Notify child configurations
        for (MutationValidator validator : childMutationValidators) {
            validator.validateMutation(type);
        }
    }

    @Override
    public ConfigurationIdentity getConfigurationIdentity() {
        String name = getName();
        ProjectIdentity projectId = domainObjectContext.getProjectIdentity();
        String projectPath = projectId == null ? null : projectId.getProjectPath().getPath();
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

    private void assertIsResolvable() {
        if (!canBeResolved) {
            throw new IllegalStateException("Resolving dependency configuration '" + name + "' is not allowed as it is defined as 'canBeResolved=false'.\nInstead, a resolvable ('canBeResolved=true') dependency configuration that extends '" + name + "' should be resolved.");
        }
    }

    private void assertIsDeclarable(String action) {
        if (!canBeDeclaredAgainst) {
            throw new IllegalStateException(action + " for configuration '" + name + "' is not allowed as it is defined as 'canBeDeclared=false'.");
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

    @Override
    public boolean usageCanBeMutated() {
        return usageCanBeMutated;
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

    /**
     * If this configuration has a role set upon creation, conditionally warn upon usage mutation.
     * Configurations with roles set upon creation should not have their usage changed. In 9.0,
     * changing the usage of a configuration with a role set upon creation will become an error.
     *
     * <p>In the below two cases, for non-legacy configurations, this method does not warn. This is
     * to avoid spamming users with these warnings, as popular third-party plugins continue to
     * violate these conditions.
     * </p>
     * <ul>
     *     <li>The configuration is detached and the new value is false.</li>
     *     <li>The current value and the new value are the same</li>
     * </ul>
     *
     * The eventual goal is that all configuration usage be specified upon creation and immutable
     * thereafter.
     */
    private void maybeWarnOnChangingUsage(String methodName, boolean current, boolean newValue) {
        if (isInLegacyRole()) {
            return;
        }

        // Error will be thrown later. Don't emit a duplicate warning.
        if (!usageCanBeMutated && (current != newValue)) {
            return;
        }

        // KGP continues to set the already-set value for a given usage even though it is already set
        boolean redundantChange = current == newValue;

        // KGP disables `consumable` on detached configurations even though this is not necessary
        boolean disableUsageForDetached = isDetachedConfiguration() && !newValue;

        // This property exists to allow KGP to test whether they have properly resolved this deprecation.
        // This property WILL be removed without warning.
        if ((redundantChange || disableUsageForDetached) &&
            !Boolean.getBoolean("org.gradle.internal.deprecation.preliminary.Configuration.redundantUsageChangeWarning.enabled")
        ) {
            return;
        }

        DeprecationLogger.deprecateAction(String.format("Calling %s(%b) on %s", methodName, newValue, this))
            .withContext("This configuration's role was set upon creation and its usage should not be changed.")
            .willBecomeAnErrorInGradle9()
            .withUpgradeGuideSection(8, "configurations_allowed_usage")
            .nagUser();
    }

    private boolean isDetachedConfiguration() {
        return this.configurationsProvider instanceof DetachedConfigurationsProvider;
    }

    @SuppressWarnings("deprecation")
    private boolean isInLegacyRole() {
        return roleAtCreation == ConfigurationRoles.LEGACY;
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
        maybeWarnOnChangingUsage("setCanBeConsumed", canBeConsumed, allowed);
        setCanBeConsumedInternal(allowed);
    }

    /**
     * Configures if a configuration can be consumed, without emitting any warnings.
     */
    private void setCanBeConsumedInternal(boolean allowed) {
        if (canBeConsumed != allowed) {
            validateMutation(MutationType.USAGE);
            canBeConsumed = allowed;
        }
    }

    @Override
    public boolean isCanBeResolved() {
        return canBeResolved;
    }

    @Override
    public void setCanBeResolved(boolean allowed) {
        maybeWarnOnChangingUsage("setCanBeResolved", canBeResolved, allowed);
        setCanBeResolvedInternal(allowed);
    }

    /**
     * Configures if a configuration can be resolved, without emitting any warnings.
     */
    private void setCanBeResolvedInternal(boolean allowed) {
        if (canBeResolved != allowed) {
            validateMutation(MutationType.USAGE);
            canBeResolved = allowed;
        }
    }

    @Override
    public boolean isCanBeDeclared() {
        return canBeDeclaredAgainst;
    }

    @Override
    public void setCanBeDeclared(boolean allowed) {
        maybeWarnOnChangingUsage("setCanBeDeclared", canBeDeclaredAgainst, allowed);
        setCanBeDeclaredInternal(allowed);
    }

    /**
     * Configures if a configuration can have dependencies declared against it, without emitting any warnings.
     */
    private void setCanBeDeclaredInternal(boolean allowed) {
        if (canBeDeclaredAgainst != allowed) {
            validateMutation(MutationType.USAGE);
            canBeDeclaredAgainst = allowed;
        }
    }

    @Override
    public void setAllowedUsageFromRole(ConfigurationRole role) {
        if (isCanBeConsumed() != role.isConsumable()) {
            setCanBeConsumedInternal(role.isConsumable());
        }
        if (isCanBeResolved() != role.isResolvable()) {
            setCanBeResolvedInternal(role.isResolvable());
        }
        if (isCanBeDeclared() != role.isDeclarable()) {
            setCanBeDeclaredInternal(role.isDeclarable());
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

    public InternalProblems getProblems() {
        return problemsService;
    }

    private void assertNotDetachedExtensionDoingExtending(Iterable<Configuration> extendsFrom) {
        if (isDetachedConfiguration()) {
            String summarizedExtensionTargets = StreamSupport.stream(extendsFrom.spliterator(), false)
                .map(c -> "'" + c.getName() + "'")
                .collect(Collectors.joining(", "));
            DeprecationLogger.deprecateAction(String.format("Calling extendsFrom on %s", this.getDisplayName()))
                .withContext(String.format("Detached configurations should not extend other configurations, this was extending: %s.", summarizedExtensionTargets))
                .willBecomeAnErrorInGradle9()
                .withUpgradeGuideSection(8, "detached_configurations_cannot_extend")
                .nagUser();
        }
    }

    public class ConfigurationResolvableDependencies implements ResolvableDependenciesInternal {

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getPath() {
            return projectPath.getPath();
        }

        @Override
        public String toString() {
            return "dependencies '" + identityPath + "'";
        }

        @Override
        public FileCollection getFiles() {
            return getIntrinsicFiles();
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
            return new DefaultResolutionResult(resolutionAccess, attributeDesugaring);
        }

        @Override
        public ArtifactCollection getArtifacts() {
            return resolutionAccess.getPublicView().getArtifacts();
        }

        @Override
        public ArtifactView artifactView(Action<? super ArtifactView.ViewConfiguration> configAction) {
            return resolutionAccess.getPublicView().artifactView(configAction);
        }

        @Override
        public AttributeContainer getAttributes() {
            return configurationAttributes;
        }

        @Override
        public ResolutionOutputsInternal getResolutionOutputs() {
            assertIsResolvable();
            return resolutionAccess.getPublicView();
        }
    }

    private class AllArtifactsProvider implements PublishArtifactSetProvider {
        @Override
        public PublishArtifactSet getPublishArtifactSet() {
            return getAllArtifacts();
        }
    }

    @Override
    public ResolutionHost getResolutionHost() {
        return new DefaultResolutionHost(this);
    }

    private static class DefaultResolutionHost implements ResolutionHost {
        private final DefaultConfiguration configuration;

        public DefaultResolutionHost(DefaultConfiguration configuration) {
            this.configuration = configuration;
        }

        @Override
        public InternalProblems getProblems() {
            return configuration.getProblems();
        }

        @Override
        public DisplayName displayName() {
            return configuration.displayName;
        }

        @Override
        public Optional<TypedResolveException> consolidateFailures(String resolutionType, Collection<Throwable> failures) {
            return Optional.ofNullable(configuration.exceptionMapper.mapFailures(failures, resolutionType, configuration.getDisplayName()));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            DefaultResolutionHost that = (DefaultResolutionHost) o;
            return configuration == that.configuration;
        }

        @Override
        public int hashCode() {
            return configuration.hashCode();
        }
    }

    @Override
    public FailureResolutions getFailureResolutions() {
        return new ConfigurationFailureResolutions(domainObjectContext, name);
    }

    private static class ConfigurationFailureResolutions implements FailureResolutions {

        private final DomainObjectContext domainObjectContext;
        private final String configurationName;

        public ConfigurationFailureResolutions(
            DomainObjectContext domainObjectContext,
            String configurationName
        ) {
            this.domainObjectContext = domainObjectContext;
            this.configurationName = configurationName;
        }

        @Override
        public List<String> forVersionConflict(Set<Conflict> conflicts) {
            ProjectIdentity projectId = domainObjectContext.getProjectIdentity();
            if (projectId == null) {
                // projectPath is null for settings execution
                return Collections.emptyList();
            }

            String taskPath = projectId.getBuildTreePath().append(Path.path("dependencyInsight")).getPath();

            ModuleVersionIdentifier identifier = conflicts.iterator().next().getVersions().get(0);
            String dependencyNotation = identifier.getGroup() + ":" + identifier.getName();

            return Collections.singletonList(String.format(
                "Run with %s --configuration %s --dependency %s to get more insight on how to solve the conflict.",
                taskPath, configurationName, dependencyNotation
            ));
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
