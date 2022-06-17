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
import org.gradle.api.Action;
import org.gradle.api.Describable;
import org.gradle.api.DomainObjectSet;
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
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.CompositeDomainObjectSet;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.ConfigurationResolver;
import org.gradle.api.internal.artifacts.DefaultDependencyConstraintSet;
import org.gradle.api.internal.artifacts.DefaultDependencySet;
import org.gradle.api.internal.artifacts.DefaultExcludeRule;
import org.gradle.api.internal.artifacts.DefaultPublishArtifactSet;
import org.gradle.api.internal.artifacts.DefaultResolverResults;
import org.gradle.api.internal.artifacts.ExcludeRuleNotationConverter;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyConstraint;
import org.gradle.api.internal.artifacts.dependencies.DependencyConstraintInternal;
import org.gradle.api.internal.artifacts.ivyservice.DefaultLenientConfiguration;
import org.gradle.api.internal.artifacts.ivyservice.ResolvedArtifactCollectingVisitor;
import org.gradle.api.internal.artifacts.ivyservice.ResolvedFileCollectionVisitor;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.RootComponentMetadataBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult.ResolvedProjectConfiguration;
import org.gradle.api.internal.artifacts.transform.DefaultExtraExecutionGraphDependenciesResolverFactory;
import org.gradle.api.internal.artifacts.transform.ExtraExecutionGraphDependenciesResolverFactory;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributeContainerWithErrorMessage;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.attributes.IncubatingAttributesChecker;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.provider.BuildableBackedSetProvider;
import org.gradle.api.internal.provider.DefaultProvider;
import org.gradle.api.internal.tasks.FailureCollectingTaskDependencyResolveContext;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.configuration.internal.UserCodeApplicationContext;
import org.gradle.internal.Actions;
import org.gradle.internal.Cast;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.ImmutableActionSet;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.component.external.model.ProjectDerivedCapability;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.deprecation.DeprecatableConfiguration;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.deprecation.DeprecationMessageBuilder;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.lazy.Lazy;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.model.CalculatedModelValue;
import org.gradle.internal.model.CalculatedValueContainer;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resolve.ModuleVersionNotFoundException;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.work.WorkerThreadRegistry;
import org.gradle.util.Path;
import org.gradle.util.internal.CollectionUtils;
import org.gradle.util.internal.ConfigureUtil;
import org.gradle.util.internal.WrapUtil;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.gradle.api.internal.artifacts.configurations.ConfigurationInternal.InternalState.ARTIFACTS_RESOLVED;
import static org.gradle.api.internal.artifacts.configurations.ConfigurationInternal.InternalState.BUILD_DEPENDENCIES_RESOLVED;
import static org.gradle.api.internal.artifacts.configurations.ConfigurationInternal.InternalState.GRAPH_RESOLVED;
import static org.gradle.api.internal.artifacts.configurations.ConfigurationInternal.InternalState.UNRESOLVED;
import static org.gradle.util.internal.ConfigureUtil.configure;

@SuppressWarnings("rawtypes")
public class DefaultConfiguration extends AbstractFileCollection implements ConfigurationInternal, MutationValidator {

    private static final Action<Throwable> DEFAULT_ERROR_HANDLER = throwable -> {
        throw UncheckedException.throwAsUncheckedException(throwable);
    };

    private final ConfigurationResolver resolver;
    private final DependencyMetaDataProvider metaDataProvider;
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
    private ProjectDependencyObservedListener dependencyObservedBroadcast;
    private final BuildOperationExecutor buildOperationExecutor;
    private final Instantiator instantiator;
    private Factory<ResolutionStrategyInternal> resolutionStrategyFactory;
    private ResolutionStrategyInternal resolutionStrategy;
    private final FileCollectionFactory fileCollectionFactory;
    private final DocumentationRegistry documentationRegistry;

    private final Set<MutationValidator> childMutationValidators = Sets.newHashSet();
    private final MutationValidator parentMutationValidator = DefaultConfiguration.this::validateParentMutation;
    private final RootComponentMetadataBuilder rootComponentMetadataBuilder;
    private final ConfigurationsProvider configurationsProvider;

    private final Path identityPath;
    private final Path path;

    // These fields are not covered by mutation lock
    private final String name;
    private final DefaultConfigurationPublications outgoing;

    private boolean visible = true;
    private boolean transitive = true;
    private Set<Configuration> extendsFrom = new LinkedHashSet<>();
    private String description;
    private final Set<Object> excludeRules = new LinkedHashSet<>();
    private Set<ExcludeRule> parsedExcludeRules;
    private boolean returnAllVariants = false;

    private final Object observationLock = new Object();
    private volatile InternalState observedState = UNRESOLVED;
    private boolean insideBeforeResolve;

    private boolean dependenciesModified;
    private boolean canBeConsumed = true;
    private boolean canBeResolved = true;

    private boolean canBeMutated = true;
    private AttributeContainerInternal configurationAttributes;
    private final DomainObjectContext domainObjectContext;
    private final ImmutableAttributesFactory attributesFactory;
    private final ConfigurationFileCollection intrinsicFiles;

    private final DisplayName displayName;
    private final UserCodeApplicationContext userCodeApplicationContext;
    private final WorkerThreadRegistry workerThreadRegistry;
    private final DomainObjectCollectionFactory domainObjectCollectionFactory;
    private final Lazy<List<DependencyConstraint>> consistentResolutionConstraints = Lazy.unsafe().of(this::consistentResolutionConstraints);

    private final AtomicInteger copyCount = new AtomicInteger(0);

    private Action<? super ConfigurationInternal> beforeLocking;

    private List<String> declarationAlternatives;
    private DeprecationMessageBuilder.WithDocumentation consumptionDeprecation;
    private List<String> resolutionAlternatives;

    private final CalculatedModelValue<ResolveState> currentResolveState;

    private ConfigurationInternal consistentResolutionSource;
    private String consistentResolutionReason;
    private ExtraExecutionGraphDependenciesResolverFactory dependenciesResolverFactory;
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
        Factory<ResolutionStrategyInternal> resolutionStrategyFactory,
        FileCollectionFactory fileCollectionFactory,
        BuildOperationExecutor buildOperationExecutor,
        Instantiator instantiator,
        NotationParser<Object, ConfigurablePublishArtifact> artifactNotationParser,
        NotationParser<Object, Capability> capabilityNotationParser,
        ImmutableAttributesFactory attributesFactory,
        RootComponentMetadataBuilder rootComponentMetadataBuilder,
        DocumentationRegistry documentationRegistry,
        UserCodeApplicationContext userCodeApplicationContext,
        ProjectStateRegistry projectStateRegistry,
        WorkerThreadRegistry workerThreadRegistry,
        DomainObjectCollectionFactory domainObjectCollectionFactory,
        CalculatedValueContainerFactory calculatedValueContainerFactory,
        DefaultConfigurationFactory defaultConfigurationFactory
    ) {
        this.userCodeApplicationContext = userCodeApplicationContext;
        this.projectStateRegistry = projectStateRegistry;
        this.workerThreadRegistry = workerThreadRegistry;
        this.domainObjectCollectionFactory = domainObjectCollectionFactory;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
        this.identityPath = domainObjectContext.identityPath(name);
        this.name = name;
        this.configurationsProvider = configurationsProvider;
        this.resolver = resolver;
        this.metaDataProvider = metaDataProvider;
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
        this.documentationRegistry = documentationRegistry;
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

        this.artifacts = new DefaultPublishArtifactSet(Describables.of(displayName, "artifacts"), ownArtifacts, fileCollectionFactory);

        this.outgoing = instantiator.newInstance(DefaultConfigurationPublications.class, displayName, artifacts, new AllArtifactsProvider(), configurationAttributes, instantiator, artifactNotationParser, capabilityNotationParser, fileCollectionFactory, attributesFactory, domainObjectCollectionFactory);
        this.rootComponentMetadataBuilder = rootComponentMetadataBuilder;
        this.currentResolveState = domainObjectContext.getModel().newCalculatedValue(ResolveState.NOT_RESOLVED);
        this.path = domainObjectContext.projectPath(name);
        this.defaultConfigurationFactory = defaultConfigurationFactory;
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

    @VisibleForTesting
    public InternalState getResolvedState() {
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
        validateMutation(MutationType.DEPENDENCIES);
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
        validateMutation(MutationType.DEPENDENCIES);
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

    @Override
    public Set<Configuration> getAll() {
        return ImmutableSet.copyOf(configurationsProvider.getAll());
    }

    @Override
    public Set<File> resolve() {
        return getFiles();
    }

    @Override
    public Iterator<File> iterator() {
        return intrinsicFiles.iterator();
    }

    @Override
    protected void visitContents(FileCollectionStructureVisitor visitor) {
        intrinsicFiles.visitContents(visitor);
    }

    @Override
    protected void appendContents(TreeFormatter formatter) {
        formatter.node("configuration: " + getIdentityPath());
    }

    @Override
    public boolean contains(File file) {
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
        return fileCollectionFromSpec(dependencySpec);
    }

    private ConfigurationFileCollection fileCollectionFromSpec(Spec<? super Dependency> dependencySpec) {
        return new ConfigurationFileCollection(new SelectedArtifactsProvider(), dependencySpec, configurationAttributes, Specs.satisfyAll(), false, false, new DefaultResolutionHost());
    }

    @Override
    public FileCollection fileCollection(Closure dependencySpecClosure) {
        return fileCollection(Specs.convertClosureToSpec(dependencySpecClosure));
    }

    @Override
    public FileCollection fileCollection(Dependency... dependencies) {
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

    private void markParentsObserved(InternalState requestedState) {
        for (Configuration configuration : extendsFrom) {
            ((ConfigurationInternal) configuration).markAsObserved(requestedState);
        }
    }

    @Override
    public ResolvedConfiguration getResolvedConfiguration() {
        return resolveToStateOrLater(ARTIFACTS_RESOLVED).getResolvedConfiguration();
    }

    private ResolveState resolveToStateOrLater(final InternalState requestedState) {
        assertIsResolvable();
        warnIfConfigurationIsDeprecatedForResolving();

        ResolveState currentState = currentResolveState.get();
        if (currentState.state.compareTo(requestedState) >= 0) {
            return currentState;
        }

        if (!domainObjectContext.getModel().hasMutableState()) {
            if (!workerThreadRegistry.isWorkerThread()) {
                // Error if we are executing in a user-managed thread.
                throw new IllegalStateException("The configuration " + identityPath.toString() + " was resolved from a thread not managed by Gradle.");
            } else {
                // We don't currently have mutable access to the project, so report a deprecation warning and then continue by attempting to acquire mutable access
                DeprecationLogger.deprecateBehaviour("Resolution of the configuration " + identityPath.toString() + " was attempted from a context different than the project context. Have a look at the documentation to understand why this is a problem and how it can be resolved.")
                    .willBeRemovedInGradle8()
                    .withUserManual("viewing_debugging_dependencies", "sub:resolving-unsafe-configuration-resolution-errors")
                    .nagUser();
                return domainObjectContext.getModel().fromMutableState(p -> resolveExclusively(requestedState));
            }
        }
        return resolveExclusively(requestedState);
    }

    private void warnIfConfigurationIsDeprecatedForResolving() {
        if (resolutionAlternatives != null) {
            DeprecationLogger.deprecateConfiguration(this.name).forResolution().replaceWith(resolutionAlternatives)
                .willBecomeAnErrorInGradle8()
                .withUpgradeGuideSection(5, "dependencies_should_no_longer_be_declared_using_the_compile_and_runtime_configurations")
                .nagUser();
        }
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
                DefaultResolverResults results = new DefaultResolverResults();
                resolver.resolveGraph(DefaultConfiguration.this, results);
                dependenciesModified = false;

                ResolveState newState = new GraphResolved(results);

                // Make the new state visible in case a dependency resolution listener queries the result, which requires the new state
                currentResolveState.set(newState);

                // Mark all affected configurations as observed
                markParentsObserved(requestedState);
                markReferencedProjectConfigurationsObserved(requestedState, results);

                if (!newState.hasError()) {
                    dependencyResolutionListeners.getSource().afterResolve(incoming);
                    // Discard listeners
                    dependencyResolutionListeners.removeAll();

                    // Use the current state, which may have changed if the listener queried the result
                    newState = currentResolveState.get();
                }
                captureBuildOperationResult(context, results);
                return newState;
            }

            private void captureBuildOperationResult(BuildOperationContext context, ResolverResults results) {
                Throwable failure = results.getFailure();
                if (failure != null) {
                    context.failed(failure);
                }
                // When dependency resolution has failed, we don't want the build operation listeners to fail as well
                // because:
                // 1. the `failed` method will have been called with the user facing error
                // 2. such an error may still lead to a valid dependency graph
                ResolutionResult resolutionResult = results.getResolutionResult();
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
        return consistentResolutionSource;
    }

    private List<DependencyConstraint> consistentResolutionConstraints() {
        if (consistentResolutionSource == null) {
            return Collections.emptyList();
        }
        assertThatConsistentResolutionIsPropertyConfigured();
        return consistentResolutionSource.getIncoming()
            .getResolutionResult()
            .getAllComponents()
            .stream()
            .map(this::registerConsistentResolutionConstraint)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }

    private void assertThatConsistentResolutionIsPropertyConfigured() {
        if (!consistentResolutionSource.isCanBeResolved()) {
            throw new InvalidUserCodeException("You can't use " + consistentResolutionSource + " as a consistent resolution source for " + this + " because it isn't a resolvable configuration.");
        }
        assertNoDependencyResolutionConsistencyCycle();
    }

    @Override
    public Supplier<List<DependencyConstraint>> getConsistentResolutionConstraints() {
        return consistentResolutionConstraints;
    }

    @Override
    public ResolveException maybeAddContext(ResolveException e) {
        return failuresWithHint(e.getCauses()).orElse(e);
    }

    private Optional<ResolveException> failuresWithHint(Collection<? extends Throwable> causes) {
        try {
            if (ignoresSettingsRepositories()) {
                boolean hasModuleNotFound = causes.stream().anyMatch(ModuleVersionNotFoundException.class::isInstance);
                if (hasModuleNotFound) {
                    return Optional.of(new ResolveExceptionWithHints(getDisplayName(), causes,
                        "The project declares repositories, effectively ignoring the repositories you have declared in the settings.",
                        "You can figure out how project repositories are declared by configuring your build to fail on project repositories.",
                        "See " + documentationRegistry.getDocumentationFor("declaring_repositories", "sub:fail_build_on_project_repositories") + " for details."));
                }
            }
        } catch (Throwable e) {
            return Optional.of(new ResolveException(getDisplayName(), ImmutableList.<Throwable>builder().addAll(causes).add(e).build()));
        }
        return Optional.empty();
    }

    private boolean ignoresSettingsRepositories() {
        if (domainObjectContext instanceof ProjectInternal) {
            ProjectInternal project = (ProjectInternal) this.domainObjectContext;
            return !project.getRepositories().isEmpty() &&
                !project.getGradle().getSettings().getDependencyResolutionManagement().getRepositories().isEmpty();
        }
        return false;
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

    private Optional<DependencyConstraint> registerConsistentResolutionConstraint(ResolvedComponentResult result) {
        if (result.getId() instanceof ModuleComponentIdentifier) {
            ModuleVersionIdentifier moduleVersion = result.getModuleVersion();
            DefaultDependencyConstraint constraint = DefaultDependencyConstraint.strictly(
                moduleVersion.getGroup(),
                moduleVersion.getName(),
                moduleVersion.getVersion());
            constraint.because(consistentResolutionReason);
            return Optional.of(constraint);
        }
        return Optional.empty();
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
        ResolverResults results = currentState.getCachedResolverResults();
        resolver.resolveArtifacts(DefaultConfiguration.this, results);
        return new ArtifactsResolved(results);
    }

    @Override
    public ExtraExecutionGraphDependenciesResolverFactory getDependenciesResolver() {
        if (dependenciesResolverFactory == null) {
            dependenciesResolverFactory = new DefaultExtraExecutionGraphDependenciesResolverFactory(new DefaultResolutionResultProvider(), domainObjectContext, calculatedValueContainerFactory,
                (attributes, filter) -> {
                    ImmutableAttributes fullAttributes = attributesFactory.concat(configurationAttributes.asImmutable(), attributes);
                    return new ConfigurationFileCollection(new SelectedArtifactsProvider(), Specs.satisfyAll(), fullAttributes, filter, false, false, new DefaultResolutionHost());
                });
        }
        return dependenciesResolverFactory;
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
                ResolverResults results = new DefaultResolverResults();
                resolver.resolveBuildDependencies(DefaultConfiguration.this, results);
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
            return new TasksFromProjectDependencies(taskName, getAllDependencies());
        } else {
            return new TasksFromDependentProjects(taskName, getName());
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

    @SuppressWarnings("unchecked")
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

    @SuppressWarnings("unchecked")
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

    @SuppressWarnings("unchecked")
    private synchronized void initAllArtifacts() {
        if (allArtifacts != null) {
            return;
        }
        DisplayName displayName = Describables.of(this.displayName, "all artifacts");

        if (!canBeMutated && extendsFrom.isEmpty()) {
            // No further mutation is allowed and there's no parent: the artifact set corresponds to this configuration own artifacts
            this.allArtifacts = new DefaultPublishArtifactSet(displayName, ownArtifacts, fileCollectionFactory);
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
            this.allArtifacts = new DefaultPublishArtifactSet(displayName, inheritedArtifacts, fileCollectionFactory);
        } else {
            this.allArtifacts = new DefaultPublishArtifactSet(displayName, ownArtifacts, fileCollectionFactory);
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

    public void setExcludeRules(Set<ExcludeRule> excludeRules) {
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

    @Deprecated
    @Override
    public String getUploadTaskName() {
        return Configurations.uploadTaskName(getName());
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
    public OutgoingVariant convertToOutgoingVariant() {
        return outgoing.convertToOutgoingVariant();
    }

    @Override
    public void collectVariants(VariantVisitor visitor) {
        outgoing.collectVariants(visitor);
    }

    @Override
    public void beforeLocking(Action<? super ConfigurationInternal> action) {
        if (canBeMutated) {
            if (beforeLocking != null) {
                beforeLocking = Actions.composite(beforeLocking, action);
            } else {
                beforeLocking = action;
            }
        }
    }

    @Override
    public boolean isCanBeMutated() {
        return canBeMutated;
    }

    @Override
    public void preventFromFurtherMutation() {
        // TODO This should use the same `MutationValidator` infrastructure that we use for other mutation types
        if (canBeMutated) {
            if (beforeLocking != null) {
                beforeLocking.execute(this);
                beforeLocking = null;
            }
            AttributeContainerInternal delegatee = configurationAttributes.asImmutable();
            configurationAttributes = new ImmutableAttributeContainerWithErrorMessage(delegatee, this.displayName);
            outgoing.preventFromFurtherMutation();
            canBeMutated = false;

            // We will only check unique attributes if this configuration is consumable, not resolvable, and has attributes itself
            if (isCanBeConsumed() && !isCanBeResolved() && !getAttributes().isEmpty()) {
                ensureUniqueAttributes();
            }
        }
    }

    private void ensureUniqueAttributes() {
        final Set<? extends ConfigurationInternal> all = (configurationsProvider != null) ? configurationsProvider.getAll() : null;
        if (all != null) {
            final Collection<? extends Capability> allCapabilities = allCapabilitiesIncludingDefault(this);

            final Consumer<ConfigurationInternal> warnIfDuplicate = otherConfiguration -> {
                if (hasSameCapabilitiesAs(allCapabilities, otherConfiguration) && hasSameAttributesAs(otherConfiguration)) {
                    DeprecationLogger.deprecateBehaviour("Consumable configurations with identical capabilities within a project must have unique attributes, but " + getDisplayName() + " and " + otherConfiguration.getDisplayName() + " contain identical attribute sets.")
                        .withAdvice("Consider adding an additional attribute to one of the configurations to disambiguate them.  Run the 'outgoingVariants' task for more details.")
                        .willBecomeAnErrorInGradle8()
                        .withUpgradeGuideSection(7, "unique_attribute_sets")
                        .nagUser();
                }
            };

            all.stream()
                .filter(Configuration::isCanBeConsumed)
                .filter(c -> !c.isCanBeResolved())
                .filter(c -> !c.isCanBeMutated())
                .filter(c -> c != this)
                .filter(c -> !c.getAttributes().isEmpty())
                .forEach(warnIfDuplicate);
        }
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
    public boolean isIncubating() {
        return IncubatingAttributesChecker.isAnyIncubating(getAttributes());
    }

    @Override
    public void outgoing(Action<? super ConfigurationPublications> action) {
        action.execute(outgoing);
    }

    @Override
    public ConfigurationInternal copy() {
        return createCopy(getDependencies(), getDependencyConstraints());
    }

    @Override
    public Configuration copyRecursive() {
        return createCopy(getAllDependencies(), getAllDependencyConstraints());
    }

    @Override
    public Configuration copy(Spec<? super Dependency> dependencySpec) {
        return createCopy(CollectionUtils.filter(getDependencies(), dependencySpec), getDependencyConstraints());
    }

    @Override
    public Configuration copyRecursive(Spec<? super Dependency> dependencySpec) {
        return createCopy(CollectionUtils.filter(getAllDependencies(), dependencySpec), getAllDependencyConstraints());
    }

    private DefaultConfiguration createCopy(Set<Dependency> dependencies, Set<DependencyConstraint> dependencyConstraints) {
        DefaultConfiguration copiedConfiguration = newConfiguration();
        // state, cachedResolvedConfiguration, and extendsFrom intentionally not copied - must re-resolve copy
        // copying extendsFrom could mess up dependencies when copy was re-resolved

        copiedConfiguration.visible = visible;
        copiedConfiguration.transitive = transitive;
        copiedConfiguration.description = description;

        copiedConfiguration.defaultDependencyActions = defaultDependencyActions;
        copiedConfiguration.withDependencyActions = withDependencyActions;
        copiedConfiguration.dependencyResolutionListeners = dependencyResolutionListeners.copy();

        copiedConfiguration.canBeConsumed = canBeConsumed;
        copiedConfiguration.canBeResolved = canBeResolved;

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

    private DefaultConfiguration newConfiguration() {
        DetachedConfigurationsProvider configurationsProvider = new DetachedConfigurationsProvider();
        RootComponentMetadataBuilder rootComponentMetadataBuilder = this.rootComponentMetadataBuilder.withConfigurationsProvider(configurationsProvider);

        String newName = getNameWithCopySuffix();

        Factory<ResolutionStrategyInternal> childResolutionStrategy = resolutionStrategy != null ? Factories.constant(resolutionStrategy.copy()) : resolutionStrategyFactory;
        DefaultConfiguration copiedConfiguration = defaultConfigurationFactory.create(
            newName,
            configurationsProvider,
            childResolutionStrategy,
            rootComponentMetadataBuilder
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
    public ComponentResolveMetadata toRootComponentMetaData() {
        return rootComponentMetadataBuilder.toRootComponentMetaData();
    }

    @Override
    public String getPath() {
        return path.getPath();
    }

    @Override
    public Path getIdentityPath() {
        return identityPath;
    }

    @Override
    public void setReturnAllVariants(boolean returnAllVariants) {
        if (!canBeMutated) {
            throw new IllegalStateException("Configuration is unmodifiable");
        }
        this.returnAllVariants = returnAllVariants;
    }

    @Override
    public boolean getReturnAllVariants() {
        return this.returnAllVariants;
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

    private class DefaultResolutionResultProvider implements ResolutionResultProvider<ResolutionResult> {
        @Override
        public ResolutionResult getTaskDependencyValue() {
            return getResultsForBuildDependencies().getResolutionResult();
        }

        @Override
        public ResolutionResult getValue() {
            return getResultsForArtifacts().getResolutionResult();
        }
    }

    private class SelectedArtifactsProvider implements ResolutionResultProvider<VisitedArtifactSet> {
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

    private static class ConfigurationFileCollection extends AbstractFileCollection {
        private final Spec<? super Dependency> dependencySpec;
        private final AttributeContainerInternal viewAttributes;
        private final Spec<? super ComponentIdentifier> componentSpec;
        private final boolean lenient;
        private final boolean allowNoMatchingVariants;
        private final ResolutionResultProvider<VisitedArtifactSet> resultProvider;
        private final ResolutionHost resolutionHost;
        private SelectedArtifactSet selectedArtifacts;

        private ConfigurationFileCollection(
            ResolutionResultProvider<VisitedArtifactSet> resultProvider,
            Spec<? super Dependency> dependencySpec,
            AttributeContainerInternal viewAttributes,
            Spec<? super ComponentIdentifier> componentSpec,
            boolean lenient,
            boolean allowNoMatchingVariants,
            ResolutionHost resolutionHost
        ) {
            this.resultProvider = resultProvider;
            this.dependencySpec = dependencySpec;
            this.viewAttributes = viewAttributes;
            this.componentSpec = componentSpec;
            this.lenient = lenient;
            this.allowNoMatchingVariants = allowNoMatchingVariants;
            this.resolutionHost = resolutionHost;
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            SelectedArtifactSet selected = resultProvider.getTaskDependencyValue().select(dependencySpec, viewAttributes, componentSpec, allowNoMatchingVariants);
            FailureCollectingTaskDependencyResolveContext collectingContext = new FailureCollectingTaskDependencyResolveContext(context);
            selected.visitDependencies(collectingContext);
            if (!lenient) {
                resolutionHost.rethrowFailure("task dependencies", collectingContext.getFailures());
            }
        }

        @Override
        public String getDisplayName() {
            return resolutionHost.displayName("files").getDisplayName();
        }

        @Override
        protected void visitContents(FileCollectionStructureVisitor visitor) {
            ResolvedFileCollectionVisitor collectingVisitor = new ResolvedFileCollectionVisitor(visitor);
            getSelectedArtifacts().visitArtifacts(collectingVisitor, lenient);
            if (!lenient) {
                resolutionHost.rethrowFailure("files", collectingVisitor.getFailures());
            }
        }

        @Override
        protected void appendContents(TreeFormatter formatter) {
            formatter.node("contains: " + getDisplayName());
        }

        private SelectedArtifactSet getSelectedArtifacts() {
            if (selectedArtifacts == null) {
                selectedArtifacts = resultProvider.getValue().select(dependencySpec, viewAttributes, componentSpec, allowNoMatchingVariants);
            }
            return selectedArtifacts;
        }
    }

    private void rethrowFailure(String type, Collection<Throwable> failures) {
        if (failures.isEmpty()) {
            return;
        }
        if (failures.size() == 1) {
            failuresWithHint(failures).ifPresent(UncheckedException::throwAsUncheckedException);
            Throwable failure = failures.iterator().next();
            if (failure instanceof ResolveException) {
                throw UncheckedException.throwAsUncheckedException(failure);
            }
        }
        throw new DefaultLenientConfiguration.ArtifactResolveException(type, getIdentityPath().toString(), getDisplayName(), failures);
    }

    private void assertIsResolvable() {
        if (!canBeResolved) {
            throw new IllegalStateException("Resolving dependency configuration '" + name + "' is not allowed as it is defined as 'canBeResolved=false'.\nInstead, a resolvable ('canBeResolved=true') dependency configuration that extends '" + name + "' should be resolved.");
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

    @Override
    public Configuration attributes(Action<? super AttributeContainer> action) {
        action.execute(configurationAttributes);
        return this;
    }

    @Override
    public boolean isCanBeConsumed() {
        return canBeConsumed;
    }

    @Override
    public void setCanBeConsumed(boolean allowed) {
        validateMutation(MutationType.ROLE);
        canBeConsumed = allowed;
    }

    @Override
    public boolean isCanBeResolved() {
        return canBeResolved;
    }

    @Override
    public void setCanBeResolved(boolean allowed) {
        validateMutation(MutationType.ROLE);
        canBeResolved = allowed;
    }

    @VisibleForTesting
    ListenerBroadcast<DependencyResolutionListener> getDependencyResolutionListeners() {
        return dependencyResolutionListeners;
    }


    @Override
    @Nullable
    public List<String> getDeclarationAlternatives() {
        return declarationAlternatives;
    }

    @Nullable
    @Override
    public DeprecationMessageBuilder.WithDocumentation getConsumptionDeprecation() {
        return consumptionDeprecation;
    }

    @Nullable
    @Override
    public List<String> getResolutionAlternatives() {
        return resolutionAlternatives;
    }

    @Override
    public boolean isFullyDeprecated() {
        return declarationAlternatives != null &&
            (!canBeConsumed || consumptionDeprecation != null) &&
            (!canBeResolved || resolutionAlternatives != null);
    }

    @Override
    public DeprecatableConfiguration deprecateForDeclaration(String... alternativesForDeclaring) {
        this.declarationAlternatives = ImmutableList.copyOf(alternativesForDeclaring);
        return this;
    }

    @Override
    public DeprecatableConfiguration deprecateForConsumption(Function<DeprecationMessageBuilder.DeprecateConfiguration, DeprecationMessageBuilder.WithDocumentation> deprecation) {
        this.consumptionDeprecation = deprecation.apply(DeprecationLogger.deprecateConfiguration(name).forConsumption());
        return this;
    }

    @Override
    public DeprecatableConfiguration deprecateForResolution(String... alternativesForResolving) {
        this.resolutionAlternatives = ImmutableList.copyOf(alternativesForResolving);
        return this;
    }

    @Override
    public Configuration shouldResolveConsistentlyWith(Configuration versionsSource) {
        this.consistentResolutionSource = (ConfigurationInternal) versionsSource;
        this.consistentResolutionReason = "version resolved in " + versionsSource + " by consistent resolution";
        return this;
    }

    @Override
    public Configuration disableConsistentResolution() {
        this.consistentResolutionSource = null;
        this.consistentResolutionReason = null;
        return this;
    }

    /**
     * Print a formatted representation of a Configuration
     */
    public String dump() {
        StringBuilder reply = new StringBuilder();

        reply.append("\nConfiguration:");
        reply.append("  class='").append(this.getClass()).append("'");
        reply.append("  name='").append(this.getName()).append("'");
        reply.append("  hashcode='").append(this.hashCode()).append("'");

        reply.append("\nLocal Dependencies:");
        if (getDependencies().size() > 0) {
            for (Dependency d : getDependencies()) {
                reply.append("\n   ").append(d);
            }
        } else {
            reply.append("\n   none");
        }

        reply.append("\nLocal Artifacts:");
        if (getArtifacts().size() > 0) {
            for (PublishArtifact a : getArtifacts()) {
                reply.append("\n   ").append(a);
            }
        } else {
            reply.append("\n   none");
        }

        reply.append("\nAll Dependencies:");
        if (getAllDependencies().size() > 0) {
            for (Dependency d : getAllDependencies()) {
                reply.append("\n   ").append(d);
            }
        } else {
            reply.append("\n   none");
        }


        reply.append("\nAll Artifacts:");
        if (getAllArtifacts().size() > 0) {
            for (PublishArtifact a : getAllArtifacts()) {
                reply.append("\n   ").append(a);
            }
        } else {
            reply.append("\n   none");
        }

        return reply.toString();
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
            return cachedResolverResults.hasError();
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

    private ConfigurationArtifactCollection artifactCollection(AttributeContainerInternal attributes, Spec<? super ComponentIdentifier> componentFilter, boolean lenient, boolean allowNoMatchingVariants) {
        ImmutableAttributes viewAttributes = attributes.asImmutable();
        DefaultResolutionHost failureHandler = new DefaultResolutionHost();
        ConfigurationFileCollection files = new ConfigurationFileCollection(new SelectedArtifactsProvider(), Specs.satisfyAll(), viewAttributes, componentFilter, lenient, allowNoMatchingVariants, failureHandler);
        return new ConfigurationArtifactCollection(files, lenient, failureHandler, calculatedValueContainerFactory);
    }

    public class ConfigurationResolvableDependencies implements ResolvableDependenciesInternal {

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getPath() {
            return path.getPath();
        }

        @Override
        public String toString() {
            return "dependencies '" + getIdentityPath() + "'";
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
            return new LenientResolutionResult(DEFAULT_ERROR_HANDLER);
        }

        @Override
        public ArtifactCollection getArtifacts() {
            return artifactCollection(configurationAttributes, Specs.satisfyAll(), false, false);
        }

        @Override
        public ArtifactView artifactView(Action<? super ArtifactView.ViewConfiguration> configAction) {
            ArtifactViewConfiguration config = createArtifactViewConfiguration();
            configAction.execute(config);
            return createArtifactView(config);
        }

        private ArtifactView createArtifactView(ArtifactViewConfiguration config) {
            ImmutableAttributes viewAttributes = config.lockViewAttributes();
            // This is a little coincidental: if view attributes have not been accessed, don't allow no matching variants
            boolean allowNoMatchingVariants = config.attributesUsed;
            ArtifactView view;
            view = new ConfigurationArtifactView(viewAttributes, config.lockComponentFilter(), config.lenient, allowNoMatchingVariants);
            return view;
        }

        private DefaultConfiguration.ArtifactViewConfiguration createArtifactViewConfiguration() {
            return instantiator.newInstance(ArtifactViewConfiguration.class, attributesFactory, configurationAttributes);
        }

        @Override
        public AttributeContainer getAttributes() {
            return configurationAttributes;
        }

        @Override
        public ResolutionResult getResolutionResult(Action<? super Throwable> errorHandler) {
            return new LenientResolutionResult(errorHandler);
        }

        private class ConfigurationArtifactView implements ArtifactView {
            private final ImmutableAttributes viewAttributes;
            private final Spec<? super ComponentIdentifier> componentFilter;
            private final boolean lenient;
            private final boolean allowNoMatchingVariants;

            ConfigurationArtifactView(ImmutableAttributes viewAttributes, Spec<? super ComponentIdentifier> componentFilter, boolean lenient, boolean allowNoMatchingVariants) {
                this.viewAttributes = viewAttributes;
                this.componentFilter = componentFilter;
                this.lenient = lenient;
                this.allowNoMatchingVariants = allowNoMatchingVariants;
            }

            @Override
            public AttributeContainer getAttributes() {
                return viewAttributes;
            }

            @Override
            public ArtifactCollection getArtifacts() {
                return artifactCollection(viewAttributes, componentFilter, lenient, allowNoMatchingVariants);
            }

            @Override
            public FileCollection getFiles() {
                // TODO maybe make detached configuration is flag is true
                return new ConfigurationFileCollection(new SelectedArtifactsProvider(), Specs.satisfyAll(), viewAttributes, componentFilter, lenient, allowNoMatchingVariants, new DefaultResolutionHost());
            }
        }

        private class LenientResolutionResult implements ResolutionResult {
            private final Action<? super Throwable> errorHandler;
            private volatile ResolutionResult delegate;

            private LenientResolutionResult(Action<? super Throwable> errorHandler) {
                this.errorHandler = errorHandler;
            }

            private void resolve() {
                if (delegate == null) {
                    synchronized (this) {
                        if (delegate == null) {
                            ResolveState currentState = resolveToStateOrLater(ARTIFACTS_RESOLVED);
                            delegate = currentState.getCachedResolverResults().getResolutionResult();
                            Throwable failure = currentState.getCachedResolverResults().consumeNonFatalFailure();
                            if (failure != null) {
                                errorHandler.execute(failure);
                            }
                        }
                    }
                }
            }

            @Override
            public ResolvedComponentResult getRoot() {
                resolve();
                return delegate.getRoot();
            }

            @Override
            public Provider<ResolvedComponentResult> getRootComponent() {
                return new DefaultProvider<>(this::getRoot);
            }

            @Override
            public Set<? extends DependencyResult> getAllDependencies() {
                resolve();
                return delegate.getAllDependencies();
            }

            @Override
            public void allDependencies(Action<? super DependencyResult> action) {
                resolve();
                delegate.allDependencies(action);
            }

            @Override
            public void allDependencies(Closure closure) {
                resolve();
                delegate.allDependencies(closure);
            }

            @Override
            public Set<ResolvedComponentResult> getAllComponents() {
                resolve();
                return delegate.getAllComponents();
            }

            @Override
            public void allComponents(Action<? super ResolvedComponentResult> action) {
                resolve();
                delegate.allComponents(action);
            }

            @Override
            public void allComponents(Closure closure) {
                resolve();
                delegate.allComponents(closure);
            }

            @Override
            public AttributeContainer getRequestedAttributes() {
                return delegate.getRequestedAttributes();
            }

            @Override
            public int hashCode() {
                resolve();
                return delegate.hashCode();
            }

            @Override
            public boolean equals(Object obj) {
                if (obj instanceof LenientResolutionResult) {
                    resolve();
                    return delegate.equals(((LenientResolutionResult) obj).delegate);
                }
                return false;
            }

            @Override
            public String toString() {
                return "lenient resolution result for " + delegate;
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

        private ImmutableAttributes lockViewAttributes() {
            if (viewAttributes == null) {
                viewAttributes = configurationAttributes.asImmutable();
            } else {
                viewAttributes = viewAttributes.asImmutable();
            }
            return viewAttributes.asImmutable();
        }
    }

    private static class ArtifactSetResult {
        private final Set<ResolvedArtifactResult> artifactResults;
        private final Set<Throwable> failures;

        ArtifactSetResult(Set<ResolvedArtifactResult> artifactResults, Set<Throwable> failures) {
            this.artifactResults = artifactResults;
            this.failures = failures;
        }
    }

    private static class ConfigurationArtifactCollection implements ArtifactCollectionInternal {
        private final ConfigurationFileCollection fileCollection;
        private final boolean lenient;
        private final CalculatedValueContainer<ArtifactSetResult, ?> result;

        ConfigurationArtifactCollection(ConfigurationFileCollection files, boolean lenient, ResolutionHost resolutionHost, CalculatedValueContainerFactory calculatedValueContainerFactory) {
            this.fileCollection = files;
            this.lenient = lenient;
            this.result = calculatedValueContainerFactory.create(resolutionHost.displayName("files"), (Supplier<ArtifactSetResult>) () -> {
                ResolvedArtifactCollectingVisitor visitor = new ResolvedArtifactCollectingVisitor();
                fileCollection.getSelectedArtifacts().visitArtifacts(visitor, lenient);

                Set<ResolvedArtifactResult> artifactResults = visitor.getArtifacts();
                Set<Throwable> failures = visitor.getFailures();

                if (!lenient) {
                    resolutionHost.rethrowFailure("artifacts", failures);
                }
                return new ArtifactSetResult(artifactResults, failures);
            });
        }

        @Override
        public FileCollection getArtifactFiles() {
            return fileCollection;
        }

        @Override
        public Set<ResolvedArtifactResult> getArtifacts() {
            ensureResolved();
            return result.get().artifactResults;
        }

        @Override
        public Provider<Set<ResolvedArtifactResult>> getResolvedArtifacts() {
            return new BuildableBackedSetProvider<>(getArtifactFiles(), new ArtifactCollectionResolvedArtifactsFactory(this));
        }

        @Override
        public Iterator<ResolvedArtifactResult> iterator() {
            ensureResolved();
            return result.get().artifactResults.iterator();
        }

        @Override
        public Collection<Throwable> getFailures() {
            ensureResolved();
            return result.get().failures;
        }

        @Override
        public void visitArtifacts(ArtifactVisitor visitor) {
            // TODO - if already resolved, use the results
            fileCollection.getSelectedArtifacts().visitArtifacts(visitor, lenient);
        }

        private void ensureResolved() {
            result.finalizeIfNotAlready();
        }
    }

    private static class ArtifactCollectionResolvedArtifactsFactory implements Factory<Set<ResolvedArtifactResult>> {

        private final ArtifactCollection artifactCollection;

        private ArtifactCollectionResolvedArtifactsFactory(ArtifactCollection artifactCollection) {
            this.artifactCollection = artifactCollection;
        }

        @Override
        public Set<ResolvedArtifactResult> create() {
            return artifactCollection.getArtifacts();
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
        public DisplayName displayName(String type) {
            return Describables.of(DefaultConfiguration.this, type);
        }

        @Override
        public void rethrowFailure(String type, Collection<Throwable> failures) {
            DefaultConfiguration.this.rethrowFailure(type, failures);
        }
    }
}
