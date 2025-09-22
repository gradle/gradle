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
import groovy.lang.Closure;
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
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.PublishArtifactSet;
import org.gradle.api.artifacts.ResolutionStrategy;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.CompositeDomainObjectSet;
import org.gradle.api.internal.ConfigurationServicesBundle;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.ConfigurationResolver;
import org.gradle.api.internal.artifacts.DefaultDependencyConstraintSet;
import org.gradle.api.internal.artifacts.DefaultDependencySet;
import org.gradle.api.internal.artifacts.DefaultExcludeRule;
import org.gradle.api.internal.artifacts.DefaultPublishArtifactSet;
import org.gradle.api.internal.artifacts.ExcludeRuleNotationConverter;
import org.gradle.api.internal.artifacts.ResolveExceptionMapper;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.dependencies.DependencyConstraintInternal;
import org.gradle.api.internal.artifacts.ivyservice.ResolutionParameters;
import org.gradle.api.internal.artifacts.ivyservice.TypedResolveException;
import org.gradle.api.internal.artifacts.resolver.DefaultResolutionOutputs;
import org.gradle.api.internal.artifacts.resolver.ResolutionAccess;
import org.gradle.api.internal.artifacts.resolver.ResolutionOutputsInternal;
import org.gradle.api.internal.artifacts.result.DefaultResolutionResult;
import org.gradle.api.internal.artifacts.result.MinimalResolutionResult;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.FreezableAttributeContainer;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.api.internal.initialization.ResettableConfiguration;
import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.Actions;
import org.gradle.internal.Cast;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.ImmutableActionSet;
import org.gradle.internal.code.UserCodeApplicationContext;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.deprecation.Documentation;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.exceptions.ResolutionProvider;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.model.CalculatedModelValue;
import org.gradle.internal.model.CalculatedValue;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.operations.dependencies.configurations.ConfigurationIdentity;
import org.gradle.util.Path;
import org.gradle.util.internal.CollectionUtils;
import org.gradle.util.internal.ConfigureUtil;
import org.gradle.util.internal.WrapUtil;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
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
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.gradle.api.internal.artifacts.configurations.ConfigurationInternal.InternalState.UNRESOLVED;
import static org.gradle.api.internal.artifacts.result.DefaultResolvedComponentResult.eachElement;
import static org.gradle.util.internal.ConfigureUtil.configure;

/**
 * The default {@link Configuration} implementation.
 */
@SuppressWarnings("rawtypes")
public abstract class DefaultConfiguration extends AbstractFileCollection implements ConfigurationInternal, MutationValidator, ResettableConfiguration {
    private final ConfigurationResolver resolver;
    private final DefaultDependencySet dependencies;
    private final DefaultDependencyConstraintSet dependencyConstraints;
    private final DefaultDomainObjectSet<Dependency> ownDependencies;
    private final DefaultDomainObjectSet<DependencyConstraint> ownDependencyConstraints;
    private @Nullable CompositeDomainObjectSet<Dependency> inheritedDependencies;
    private @Nullable CompositeDomainObjectSet<DependencyConstraint> inheritedDependencyConstraints;
    private @Nullable DefaultDependencySet allDependencies;
    private @Nullable DefaultDependencyConstraintSet allDependencyConstraints;
    private ImmutableActionSet<DependencySet> defaultDependencyActions = ImmutableActionSet.empty();
    private ImmutableActionSet<DependencySet> withDependencyActions = ImmutableActionSet.empty();
    private final DefaultPublishArtifactSet artifacts;
    private final DefaultDomainObjectSet<PublishArtifact> ownArtifacts;
    private @Nullable CompositeDomainObjectSet<PublishArtifact> inheritedArtifacts;
    private @Nullable DefaultPublishArtifactSet allArtifacts;
    private final ConfigurationResolvableDependencies resolvableDependencies;
    private ListenerBroadcast<DependencyResolutionListener> dependencyResolutionListeners;

    private final Path identityPath;
    private final Path projectPath;

    private final String name;
    private final boolean isDetached;
    private final DefaultConfigurationPublications outgoing;

    private boolean visible = true;
    private boolean transitive = true;
    private Set<Configuration> extendsFrom = new LinkedHashSet<>();
    private @Nullable String description;
    private final Set<Object> excludeRules = new LinkedHashSet<>();
    private @Nullable Set<ExcludeRule> parsedExcludeRules;

    private boolean canBeConsumed;
    private boolean canBeResolved;
    private boolean canBeDeclaredAgainst;
    private final boolean consumptionDeprecated;
    private final boolean resolutionDeprecated;
    private final boolean declarationDeprecated;
    private boolean usageCanBeMutated = true;
    private final ConfigurationRole roleAtCreation;

    // This field is reflectively accessed by Nebula:
    // https://github.com/nebula-plugins/gradle-resolution-rules-plugin/blob/db24ee7e0b5c5c6f6327cdfd377e90e505bb1fd2/src/main/kotlin/nebula/plugin/resolutionrules/configurations.kt#L59
    private InternalState observedState = UNRESOLVED;
    private @Nullable Supplier<String> observationReason = null;
    boolean dependenciesObserved = false;

    private final FreezableAttributeContainer configurationAttributes;
    private final DomainObjectContext domainObjectContext;
    private final ResolutionAccess resolutionAccess;
    private @Nullable FileCollectionInternal intrinsicFiles;

    private final DisplayName displayName;
    private final UserCodeApplicationContext userCodeApplicationContext;

    private final AtomicInteger copyCount = new AtomicInteger();

    private List<String> declarationAlternatives = ImmutableList.of();
    private List<String> resolutionAlternatives = ImmutableList.of();

    private final CalculatedModelValue<Optional<ResolverResults>> currentResolveState;

    private @Nullable ConfigurationInternal consistentResolutionSource;
    private @Nullable String consistentResolutionReason;

    /** This factory can't be extracted to the services bundle, as it would create a circular dependency between those two types. */
    private final DefaultConfigurationFactory defaultConfigurationFactory;

    /** This factory has some unique usages during copy, so it can't be extracted to the services bundle. */
    private Factory<ResolutionStrategyInternal> resolutionStrategyFactory;
    private @Nullable ResolutionStrategyInternal resolutionStrategy;

    private final ConfigurationServicesBundle configurationServices;

    /**
     * To create an instance, use {@link DefaultConfigurationFactory#create}.
     */
    public DefaultConfiguration(
        ConfigurationServicesBundle configurationServices,
        DomainObjectContext domainObjectContext,
        String name,
        boolean isDetached,
        ConfigurationResolver resolver,
        ListenerBroadcast<DependencyResolutionListener> dependencyResolutionListeners,
        Factory<ResolutionStrategyInternal> resolutionStrategyFactory,
        NotationParser<Object, ConfigurablePublishArtifact> artifactNotationParser,
        NotationParser<Object, Capability> capabilityNotationParser,
        UserCodeApplicationContext userCodeApplicationContext,
        DefaultConfigurationFactory defaultConfigurationFactory,
        ConfigurationRole roleAtCreation,
        boolean lockUsage
    ) {
        super(configurationServices.getTaskDependencyFactory());
        this.userCodeApplicationContext = userCodeApplicationContext;
        this.identityPath = domainObjectContext.identityPath(name);
        this.projectPath = domainObjectContext.projectPath(name);
        this.name = name;
        this.isDetached = isDetached;
        this.resolver = resolver;
        this.resolutionStrategyFactory = resolutionStrategyFactory;
        this.dependencyResolutionListeners = dependencyResolutionListeners;
        this.domainObjectContext = domainObjectContext;

        this.displayName = Describables.memoize(new ConfigurationDescription(identityPath));
        this.configurationAttributes = new FreezableAttributeContainer(configurationServices.getAttributesFactory().mutable(), this.displayName);

        this.resolutionAccess = new ConfigurationResolutionAccess();
        this.resolvableDependencies = configurationServices.getObjectFactory().newInstance(ConfigurationResolvableDependencies.class, this);

        this.ownDependencies = (DefaultDomainObjectSet<Dependency>) configurationServices.getDomainObjectCollectionFactory().newDomainObjectSet(Dependency.class);
        this.ownDependencies.beforeCollectionChanges(validateMutationType(this, MutationType.DEPENDENCIES));
        this.ownDependencyConstraints = (DefaultDomainObjectSet<DependencyConstraint>) configurationServices.getDomainObjectCollectionFactory().newDomainObjectSet(DependencyConstraint.class);
        this.ownDependencyConstraints.beforeCollectionChanges(validateMutationType(this, MutationType.DEPENDENCIES));

        this.dependencies = new DefaultDependencySet(Describables.of(displayName, "dependencies"), this, ownDependencies);
        this.dependencyConstraints = new DefaultDependencyConstraintSet(Describables.of(displayName, "dependency constraints"), this, ownDependencyConstraints);

        this.ownArtifacts = (DefaultDomainObjectSet<PublishArtifact>) configurationServices.getDomainObjectCollectionFactory().newDomainObjectSet(PublishArtifact.class);
        this.ownArtifacts.beforeCollectionChanges(validateMutationType(this, MutationType.ARTIFACTS));

        this.artifacts = new DefaultPublishArtifactSet(Describables.of(displayName, "artifacts"), ownArtifacts, configurationServices.getFileCollectionFactory(), taskDependencyFactory);

        this.outgoing = configurationServices.getObjectFactory().newInstance(DefaultConfigurationPublications.class, displayName, artifacts, new AllArtifactsProvider(), configurationAttributes, artifactNotationParser, capabilityNotationParser, configurationServices.getFileCollectionFactory(), configurationServices.getAttributesFactory(), configurationServices.getDomainObjectCollectionFactory(), taskDependencyFactory);
        this.currentResolveState = domainObjectContext.getModel().newCalculatedValue(Optional.empty());
        this.defaultConfigurationFactory = defaultConfigurationFactory;

        this.canBeConsumed = roleAtCreation.isConsumable();
        this.canBeResolved = roleAtCreation.isResolvable();
        this.canBeDeclaredAgainst = roleAtCreation.isDeclarable();
        this.consumptionDeprecated = roleAtCreation.isConsumptionDeprecated();
        this.resolutionDeprecated = roleAtCreation.isResolutionDeprecated();
        this.declarationDeprecated = roleAtCreation.isDeclarationAgainstDeprecated();
        this.usageCanBeMutated = !lockUsage;
        this.roleAtCreation = roleAtCreation;

        this.configurationServices = configurationServices;
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
    @Deprecated
    public boolean isVisible() {
        DeprecationLogger.deprecateMethod(Configuration.class, "isVisible")
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "deprecate-visible-property")
            .nagUser();
        return visible;
    }

    @Override
    @Deprecated
    public Configuration setVisible(boolean visible) {
        validateMutation(MutationType.BASIC_STATE);
        // TODO: Create a deprecation warning once https://youtrack.jetbrains.com/issue/KT-78754 is resolved
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
                throw new InvalidUserDataException(String.format(
                    "%s in %s cannot extend %s from %s. Configurations can only extend from configurations in the same context.",
                    displayName.getCapitalizedDisplayName(),
                    this.domainObjectContext.getDisplayName(),
                    other.getDisplayName(),
                    other.getDomainObjectContext().getDisplayName()
                ));
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
        validateMutation(MutationType.BASIC_STATE);
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
        warnOrFailOnInvalidUsage("defaultDependencies(Action)", ProperMethodUsage.DECLARABLE_AGAINST);

        // For backwards compatibility, we permit more than just dependencies to be
        // mutated in this callback, which is why we don't use MutationType.DEPENDENCIES here
        validateMutation(MutationType.BASIC_STATE);

        defaultDependencyActions = defaultDependencyActions.add(configurationServices.getCollectionCallbackActionDecorator().decorate(dependencies -> {
            if (dependencies.isEmpty()) {
                action.execute(dependencies);
            }
        }));
        return this;
    }

    @Override
    public Configuration withDependencies(final Action<? super DependencySet> action) {
        // For backwards compatibility, we permit more than just dependencies to be
        // mutated in this callback, which is why we don't use MutationType.DEPENDENCIES here
        validateMutation(MutationType.BASIC_STATE);

        withDependencyActions = withDependencyActions.add(configurationServices.getCollectionCallbackActionDecorator().decorate(action));
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

    private FileCollectionInternal getIntrinsicFiles() {
        if (intrinsicFiles == null) {
            assertIsResolvable();
            intrinsicFiles = resolutionAccess.getPublicView().getFiles();
        }
        return intrinsicFiles;
    }

    @Override
    public Set<File> resolve() {
        warnOrFailOnInvalidUsage("resolve()", ProperMethodUsage.RESOLVABLE);
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
     * @implNote Usage: This method should only be called on resolvable configurations and throws an exception if
     * called on a configuration that does not permit this usage.
     */
    @Override
    public boolean contains(File file) {
        warnOrFailOnInvalidUsage("contains(File)", ProperMethodUsage.RESOLVABLE);
        return getIntrinsicFiles().contains(file);
    }

    @Override
    public boolean isEmpty() {
        return getIntrinsicFiles().isEmpty();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote Usage: This method can only be called on resolvable configurations and will throw an exception if
     * called on a configuration that does not permit this usage.
     */
    @Override
    public ResolvedConfiguration getResolvedConfiguration() {
        warnOrFailOnInvalidUsage("getResolvedConfiguration()", ProperMethodUsage.RESOLVABLE);
        return resolutionAccess.getResults().getValue().getLegacyResults().getResolvedConfiguration();
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static Boolean isFullyResolved(Optional<ResolverResults> currentState) {
        return currentState.map(ResolverResults::isFullyResolved).orElse(false);
    }

    private class ConfigurationResolutionAccess implements ResolutionAccess {

        @Override
        public ResolutionHost getHost() {
            return new DefaultResolutionHost(identityPath, displayName, configurationServices.getProblems(), configurationServices.getExceptionMapper());
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
                configurationServices.getCalculatedValueContainerFactory(),
                configurationServices.getAttributesFactory(),
                configurationServices.getAttributeDesugaring(),
                configurationServices.getObjectFactory()
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
        if (isFullyResolved(currentState)) {
            return currentState.get();
        }

        ResolverResults newState;
        if (!domainObjectContext.getModel().hasMutableState()) {
            throw new IllegalResolutionException("Resolution of the " + displayName.getDisplayName() + " was attempted without an exclusive lock. This is unsafe and not allowed.");
        } else {
            newState = resolveExclusivelyIfRequired();
        }

        return newState;
    }

    private ResolverResults resolveExclusivelyIfRequired() {
        return currentResolveState.update(currentState -> {
            if (isFullyResolved(currentState)) {
                return currentState;
            }

            return Optional.of(resolveGraphInBuildOperation());
        }).get();
    }

    /**
     * Must be called from {@link #resolveExclusivelyIfRequired} only.
     */
    private ResolverResults resolveGraphInBuildOperation() {
        return configurationServices.getBuildOperationRunner().call(new CallableBuildOperation<ResolverResults>() {
            @Override
            public ResolverResults call(BuildOperationContext context) {
                runDependencyActions();
                dependencyResolutionListeners.getSource().beforeResolve(getIncoming());

                ResolverResults results;
                try {
                    results = resolver.resolveGraph(DefaultConfiguration.this);
                } catch (Exception e) {
                    throw configurationServices.getExceptionMapper().mapFailure(e, "dependencies", displayName.getDisplayName());
                }

                // Make the new state visible in case a dependency resolution listener queries the result, which requires the new state
                currentResolveState.set(Optional.of(results));

                dependencyResolutionListeners.getSource().afterResolve(getIncoming());

                // Discard State
                dependencyResolutionListeners.removeAll();
                if (resolutionStrategy != null) {
                    resolutionStrategy.maybeDiscardStateRequiredForGraphResolution();
                }

                captureBuildOperationResult(context, results);
                return results;
            }

            private void captureBuildOperationResult(BuildOperationContext context, ResolverResults results) {
                // When dependency resolution has failed, we don't want the build operation listeners to fail as well
                // because:
                // 1. the `failed` method will have been called with the user facing error
                // 2. such an error may still lead to a valid dependency graph
                MinimalResolutionResult resolutionResult = results.getVisitedGraph().getResolutionResult();
                context.setResult(new ResolveConfigurationResolutionBuildOperationResult(
                    resolutionResult.getRootSource(),
                    resolutionResult.getRequestedAttributes(),
                    configurationServices.getAttributesFactory()
                ));
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                String displayName = "Resolve dependencies of " + identityPath;
                ProjectIdentity projectId = domainObjectContext.getProjectIdentity();
                String projectPathString = null;
                if (!domainObjectContext.isScript()) {
                    if (projectId != null) {
                        projectPathString = projectId.getProjectPath().asString();
                    }
                }
                return BuildOperationDescriptor.displayName(displayName)
                    .progressDisplayName(displayName)
                    .details(new ResolveConfigurationResolutionBuildOperationDetails(
                        getName(),
                        domainObjectContext.isScript(),
                        getDescription(),
                        domainObjectContext.getBuildPath().asString(),
                        projectPathString,
                        visible,
                        isTransitive(),
                        resolver.getAllRepositories()
                    ));
            }
        });
    }


    /**
     * {@inheritDoc}
     *
     * @implNote Usage: This method can only be called on resolvable configurations and will throw an exception if
     * called on a configuration that does not permit this usage.
     */
    @Override
    public ConfigurationInternal getConsistentResolutionSource() {
        warnOrFailOnInvalidInternalAPIUsage("getConsistentResolutionSource()", ProperMethodUsage.RESOLVABLE);
        return consistentResolutionSource;
    }

    @Override
    public ImmutableList<ResolutionParameters.ModuleVersionLock> getConsistentResolutionVersionLocks() {
        if (consistentResolutionSource == null) {
            return ImmutableList.of();
        }

        assertThatConsistentResolutionIsPropertyConfigured();
        ResolvedComponentResult root = consistentResolutionSource.getIncoming().getResolutionResult().getRoot();

        ImmutableList.Builder<ResolutionParameters.ModuleVersionLock> locks = ImmutableList.builder();
        eachElement(root, component -> {
            if (component.getId() instanceof ModuleComponentIdentifier) {
                ModuleComponentIdentifier moduleId = (ModuleComponentIdentifier) component.getId();
                locks.add(new ResolutionParameters.ModuleVersionLock(
                    moduleId.getModuleIdentifier(),
                    moduleId.getVersion(),
                    consistentResolutionReason,
                    true
                ));
            }
        }, Actions.doNothing(), new HashSet<>());
        return locks.build();
    }

    private void assertThatConsistentResolutionIsPropertyConfigured() {
        if (!consistentResolutionSource.isCanBeResolved()) {
            throw new InvalidUserCodeException("You can't use " + consistentResolutionSource + " as a consistent resolution source for " + this + " because it isn't a resolvable configuration.");
        }

        // Ensure there are no cycles in the consistent resolution graph.
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

    /**
     * {@inheritDoc}
     *
     * @implNote Usage: This method can only be called on resolvable configurations and will throw an exception if
     * called on a configuration that does not permit this usage.
     */
    @Override
    public <T> T callAndResetResolutionState(Factory<T> factory) {
        warnOrFailOnInvalidInternalAPIUsage("callAndResetResolutionState(Factory)", ProperMethodUsage.RESOLVABLE);
        try {
            // Prevent the state required for resolution from being discarded if anything in the
            // factory resolves this configuration
            getResolutionStrategy().setKeepStateRequiredForGraphResolution(true);

            T value = factory.create();

            // Reset this configuration to an unresolved state
            currentResolveState.set(Optional.empty());

            return value;
        } finally {
            getResolutionStrategy().setKeepStateRequiredForGraphResolution(false);
        }
    }

    private ResolverResults resolveGraphForBuildDependenciesIfRequired() {
        assertIsResolvable();
        return currentResolveState.update(initial -> {
            if (!initial.isPresent()) {

                CalculatedValue<ResolverResults> futureCompleteResults = configurationServices.getCalculatedValueContainerFactory().create(Describables.of("Full results for", getName()), context -> {
                    Optional<ResolverResults> currentState = currentResolveState.get();
                    if (!isFullyResolved(currentState)) {
                        // Do not validate that the current thread holds the project lock.
                        // TODO: Should instead assert that the results are available and fail if not.
                        return resolveExclusivelyIfRequired();
                    }
                    return currentState.get();
                });

                try {
                    return Optional.of(resolver.resolveBuildDependencies(this, futureCompleteResults));
                } catch (Exception e) {
                    throw configurationServices.getExceptionMapper().mapFailure(e, "dependencies", displayName.getDisplayName());
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
            }, taskDependencyFactory, configurationServices.getProjectStateRegistry());
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
        inheritedDependencies = configurationServices.getDomainObjectCollectionFactory().newDomainObjectSet(Dependency.class, ownDependencies);
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
        inheritedDependencyConstraints = configurationServices.getDomainObjectCollectionFactory().newDomainObjectSet(DependencyConstraint.class, ownDependencyConstraints);
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

        if (isObserved() && extendsFrom.isEmpty()) {
            // No further mutation is allowed and there's no parent: the artifact set corresponds to this configuration own artifacts
            this.allArtifacts = new DefaultPublishArtifactSet(displayName, ownArtifacts, configurationServices.getFileCollectionFactory(), taskDependencyFactory);
            return;
        }

        if (!isObserved()) {
            // If the configuration can still be mutated, we need to create a composite
            inheritedArtifacts = configurationServices.getDomainObjectCollectionFactory().newDomainObjectSet(PublishArtifact.class, ownArtifacts);
        }
        for (Configuration configuration : this.extendsFrom) {
            PublishArtifactSet allArtifacts = configuration.getAllArtifacts();
            if (inheritedArtifacts != null || !allArtifacts.isEmpty()) {
                if (inheritedArtifacts == null) {
                    // This configuration cannot be mutated, but some parent configurations provide artifacts
                    inheritedArtifacts = configurationServices.getDomainObjectCollectionFactory().newDomainObjectSet(PublishArtifact.class, ownArtifacts);
                }
                inheritedArtifacts.addCollection(allArtifacts);
            }
        }
        if (inheritedArtifacts != null) {
            this.allArtifacts = new DefaultPublishArtifactSet(displayName, inheritedArtifacts, configurationServices.getFileCollectionFactory(), taskDependencyFactory);
        } else {
            this.allArtifacts = new DefaultPublishArtifactSet(displayName, ownArtifacts, configurationServices.getFileCollectionFactory(), taskDependencyFactory);
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
        boolean immutable = isObserved() || currentResolveState.get().isPresent();
        return !immutable;
    }

    @Override
    public void markAsObserved(String reason) {
        if (isObserved()) {
            return;
        }

        runActionInHierarchy(conf -> {
            if (!conf.isObserved()) {
                conf.observationReason = () -> {
                    String target = conf == this ? "the configuration" : "the configuration's child " + this.getDisplayName();
                    return target + " was " + reason;
                };

                // This field is only set for compatibility with Nebula
                conf.observedState = InternalState.OBSERVED;

                conf.configurationAttributes.freeze();
                conf.outgoing.preventFromFurtherMutation(conf.observationReason);
                conf.preventUsageMutation();
            }
        });
    }

    @Override
    public void markDependenciesObserved() {
        if (!isObserved()) {
            throw new IllegalStateException("Cannot observe dependencies before markAsObserved(String) has been called.");
        }

        this.dependenciesObserved = true;
    }

    private boolean isObserved() {
        return observationReason != null;
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

    /**
     * {@inheritDoc}
     *
     * @implNote Usage: This method can only be called on resolvable configurations and will throw an exception if
     * called on a configuration that does not permit this usage.
     */
    @Override
    public ConfigurationInternal copy() {
        warnOrFailOnInvalidUsage("copy()", ProperMethodUsage.RESOLVABLE);
        return createCopy(getDependencies(), getDependencyConstraints());
    }

    /**
     * {@inheritDoc}
     *
     * @implNote Usage: This method can only be called on resolvable configurations and will throw an exception if
     * called on a configuration that does not permit this usage.
     */
    @Override
    public Configuration copyRecursive() {
        warnOrFailOnInvalidUsage("copyRecursive()", ProperMethodUsage.RESOLVABLE);
        return createCopy(getAllDependencies(), getAllDependencyConstraints());
    }

    /**
     * {@inheritDoc}
     *
     * @implNote Usage: This method can only be called on resolvable configurations and will throw an exception if
     * called on a configuration that does not permit this usage.
     */
    @Override
    public Configuration copy(Spec<? super Dependency> dependencySpec) {
        warnOrFailOnInvalidUsage("copy(Spec)", ProperMethodUsage.RESOLVABLE);
        return createCopy(CollectionUtils.filter(getDependencies(), dependencySpec), getDependencyConstraints());
    }

    @Override
    public Configuration copyRecursive(Spec<? super Dependency> dependencySpec) {
        warnOrFailOnInvalidUsage("copyRecursive(Spec)", ProperMethodUsage.RESOLVABLE);
        return createCopy(CollectionUtils.filter(getAllDependencies(), dependencySpec), getAllDependencyConstraints());
    }

    /**
     * Instead of copying a configuration's roles outright, we allow copied configurations
     * to assume any role. However, any roles which were previously disabled will become
     * deprecated in the copied configuration.
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
        Factory<ResolutionStrategyInternal> childResolutionStrategy = resolutionStrategy != null ? Factories.constant(resolutionStrategy.copy()) : resolutionStrategyFactory;

        @SuppressWarnings("deprecation")
        ConfigurationRole role = ConfigurationRoles.RESOLVABLE_DEPENDENCY_SCOPE;
        return defaultConfigurationFactory.create(
            newName,
            true,
            resolver,
            childResolutionStrategy,
            role
        );
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
    public void validateMutation(MutationType type) {
        if (isMutationForbidden(type)) {
            throw new InvalidUserCodeException(
                String.format("Cannot mutate the %s of %s after %s. ", type, this.getDisplayName(), observationReason.get()) +
                    "After a configuration has been observed, it should not be modified."
            );
        }

        if (type == MutationType.USAGE) {
            assertUsageIsMutable();
        }
    }

    /**
     * Given the type of mutation, determine based on the observation state of this
     * configuration whether the mutation is forbidden or if it may proceed.
     */
    private boolean isMutationForbidden(MutationType type) {
        if (observationReason == null) {
            // This configuration has not been observed, and so is still mutable.
            // No reason to throw an exception.
            return false;
        }

        if (type == MutationType.STRATEGY && !isFullyResolved(currentResolveState.get())) {
            // TODO: Eventually this should become an error, but plugins (Android?) are mutating the
            // resolution strategy in beforeResolve in order to save memory.
            return false;
        }

        if (type == MutationType.DEPENDENCIES ||
            type == MutationType.DEPENDENCY_ATTRIBUTES ||
            type == MutationType.DEPENDENCY_CONSTRAINT_ATTRIBUTES
        ) {
            // When building variant metadata, dependencies are observed lazily after attributes, capabilities, etc.
            // We allow these to be marked as observed separately from the remainder of its state.
            return dependenciesObserved;
        }

        // Otherwise, non-dependency state has been observed and is therefore non-mutable.
        return true;
    }

    @Override
    public ConfigurationIdentity getConfigurationIdentity() {
        String name = getName();
        ProjectIdentity projectId = domainObjectContext.getProjectIdentity();
        String projectPath = projectId == null ? null : projectId.getProjectPath().asString();
        String buildPath = domainObjectContext.getBuildPath().toString();
        return new DefaultConfigurationIdentity(buildPath, projectPath, name);
    }

    private boolean isProperUsage(ProperMethodUsage... properUsages) {
        ConfigurationInternal conf = this;
        return Arrays.stream(properUsages).anyMatch(pu -> pu.isAllowed(conf));
    }

    /**
     * Checks if the only usages that allow this method are also deprecated.
     *
     * @param properUsages the usages to check against
     * @return {@code true} if so; {@code false} otherwise
     */
    private boolean isExclusivelyDeprecatedUsage(ProperMethodUsage... properUsages) {
        ConfigurationInternal conf = this;
        return Arrays.stream(properUsages)
            .filter(pu -> pu.isAllowed(conf))
            .allMatch(pu -> pu.isDeprecated(conf));
    }

    // TODO: This causes redundant deprecation logs when we call internal methods to support
    //       features on deprecated configurations. We already emit deprecation warnings
    //       when using public deprecated methods, we should not emit them again for internal API usage.
    private void warnOrFailOnInvalidInternalAPIUsage(String methodName, ProperMethodUsage... properUsages) {
        warnOrFailOnInvalidUsage(methodName, true, properUsages);
    }

    private void warnOrFailOnInvalidUsage(String methodName, ProperMethodUsage... properUsages) {
        warnOrFailOnInvalidUsage(methodName, false, properUsages);
    }

    private void warnOrFailOnInvalidUsage(String methodName, boolean allowDeprecated, ProperMethodUsage... properUsages) {
        if (!isProperUsage(properUsages)) {
            String currentUsageDesc = UsageDescriber.describeCurrentUsage(this);
            String properUsageDesc = ProperMethodUsage.summarizeProperUsage(properUsages);
            String msgTemplate = "Calling configuration method '%s' is not allowed for configuration '%s', which has permitted usage(s):\n" +
                "%s\n" +
                "This method is only meant to be called on configurations which allow the %susage(s): '%s'.";

            GradleException ex = new GradleException(String.format(msgTemplate, methodName, getName(), currentUsageDesc, allowDeprecated ? "" : "(non-deprecated) ", properUsageDesc));
            ProblemId id = ProblemId.create("method-not-allowed", "Method call not allowed", GradleCoreProblemGroup.configurationUsage());
            throw configurationServices.getProblems().getInternalReporter().throwing(ex, id, spec -> {
                spec.contextualLabel(ex.getMessage());
                spec.severity(Severity.ERROR);
            });
        } else if (isExclusivelyDeprecatedUsage(properUsages)) {
            DeprecationLogger.deprecateAction(String.format("Calling %s on %s", methodName, this))
                .withContext("This configuration does not allow this method to be called.")
                .willBecomeAnErrorInGradle10()
                .withUpgradeGuideSection(8, "configurations_allowed_usage")
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
     * @implNote Usage: This method can only be called on consumable or resolvable configurations and will throw an exception if
     * called on a configuration that does not permit this usage.
     */
    @Override
    public Configuration attributes(Action<? super AttributeContainer> action) {
        warnOrFailOnInvalidUsage("attributes(Action)", ProperMethodUsage.CONSUMABLE, ProperMethodUsage.RESOLVABLE);
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
            // Don't print role message for configurations with all usages - users might not have actively chosen this role
            if (roleAtCreation != ConfigurationRoles.ALL) {
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
     * If this configuration has a role set upon creation, conditionally fail upon usage mutation.
     * <p>
     * Configurations with roles set upon creation should not have their usage changed.
     * <p>
     * For <strong>redundant</strong>, where a method is called but no change in the usage occurs, this method does not fail. This is
     * to allow plugins utilizing this behavior to continue to function, as popular third-party plugins continue to
     * violate these conditions.  However, it may emit a warning on redundant changes if a special flag is set.
     * <p>
     * The eventual goal is that all configuration usage be specified upon creation and immutable
     * thereafter.
     */
    private void checkChangingUsage(String methodName, boolean current, boolean newValue) {
        if (hasAllUsages()) {
            // We currently allow configurations with all usages -- those that are created with
            // `create` and `register` -- to have mutable roles. This is likely to change in the future
            // when we deprecate any configuration with mutable roles.
            return;
        }

        boolean redundantChange = current == newValue;

        // Error will be thrown later. Don't emit a duplicate warning.
        if (!usageCanBeMutated && !redundantChange) {
            return;
        }

        // KGP continues to set the already-set value for a given usage even though it is already set
        // This property exists to allow KGP to test whether they have properly stopped making unnecessary redundant
        // changes to detachedConfigurations.
        // This property WILL be removed without warning and should be removed in Gradle 9.x.
        boolean extraWarningsEnabled = Boolean.getBoolean("org.gradle.internal.deprecation.preliminary.Configuration.redundantUsageChangeWarning.enabled");

        if (redundantChange) {
            // Remove this condition in Gradle 9.x and warn on every redundant change, in Gradle 10 this should fail.
            if (extraWarningsEnabled) {
                warnAboutChangingUsage(methodName, newValue);
            }
        } else {
            if (isDetachedConfiguration() && !newValue) {
                // This is an actual change, and permitting it is not desired behavior, but we haven't deprecated
                // changing detached confs usages to false as of 9.0, so we have to permit even these non-redundant changes,
                // but we can at least warn if the flag is set.
                // Remove this check and warn on every actual change to a detached conf in Gradle 9.x, in Gradle 10 this should fail.
                if (extraWarningsEnabled) {
                    warnAboutChangingUsage(methodName, newValue);
                }
            } else {
                failDueToChangingUsage(methodName, newValue);
            }
        }
    }

    private void warnAboutChangingUsage(String methodName, boolean newValue) {
        DeprecationLogger.deprecateAction(String.format("Calling %s(%b) on %s", methodName, newValue, this))
            .withContext("This configuration's role was set upon creation and its usage should not be changed.")
            .willBecomeAnErrorInGradle10()
            .withUpgradeGuideSection(8, "configurations_allowed_usage")
            .nagUser();
    }

    private void failDueToChangingUsage(String methodName, boolean newValue) {
        GradleException ex = new GradleException(String.format("Calling %s(%b) on %s is not allowed.  This configuration's role was set upon creation and its usage should not be changed.", methodName, newValue, this));
        ProblemId id = ProblemId.create("method-not-allowed", "Method call not allowed", GradleCoreProblemGroup.configurationUsage());
        throw configurationServices.getProblems().getInternalReporter().throwing(ex, id, spec -> {
            spec.contextualLabel(ex.getMessage());
            spec.severity(Severity.ERROR);
        });
    }

    @Override
    public boolean isDetachedConfiguration() {
        return isDetached;
    }

    @SuppressWarnings("deprecation")
    private boolean hasAllUsages() {
        return roleAtCreation == ConfigurationRoles.ALL;
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
        checkChangingUsage("setCanBeConsumed", canBeConsumed, allowed);
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
        checkChangingUsage("setCanBeResolved", canBeResolved, allowed);
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
        checkChangingUsage("setCanBeDeclared", canBeDeclaredAgainst, allowed);
        if (canBeDeclaredAgainst != allowed) {
            validateMutation(MutationType.USAGE);
            canBeDeclaredAgainst = allowed;
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

    /**
     * {@inheritDoc}
     *
     * @implNote Usage: This method can only be called on resolvable configurations and will throw an exception if
     * called on a configuration that does not permit this usage.
     */
    @Override
    public void addResolutionAlternatives(String... alternativesForResolving) {
        this.resolutionAlternatives = ImmutableList.<String>builder()
            .addAll(resolutionAlternatives)
            .addAll(Arrays.asList(alternativesForResolving))
            .build();
    }

    @Override
    public Configuration shouldResolveConsistentlyWith(Configuration versionsSource) {
        warnOrFailOnInvalidUsage("shouldResolveConsistentlyWith(Configuration)", ProperMethodUsage.RESOLVABLE);
        this.consistentResolutionSource = (ConfigurationInternal) versionsSource;
        this.consistentResolutionReason = "version resolved in " + versionsSource + " by consistent resolution";
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote Usage: This method can only be called on resolvable configurations and will throw an exception if
     * called on a configuration that does not permit this usage.
     */
    @Override
    public Configuration disableConsistentResolution() {
        warnOrFailOnInvalidUsage("disableConsistentResolution()", ProperMethodUsage.RESOLVABLE);
        this.consistentResolutionSource = null;
        this.consistentResolutionReason = null;
        return this;
    }

    @Override
    public ConfigurationRole getRoleAtCreation() {
        return roleAtCreation;
    }

    public InternalProblems getProblems() {
        return configurationServices.getProblems();
    }

    private void assertNotDetachedExtensionDoingExtending(Iterable<Configuration> extendsFrom) {
        if (isDetachedConfiguration()) {
            String summarizedExtensionTargets = StreamSupport.stream(extendsFrom.spliterator(), false)
                .map(ConfigurationInternal.class::cast)
                .map(ConfigurationInternal::getDisplayName)
                .collect(Collectors.joining(", "));
            GradleException ex = new GradleException(getDisplayName() + " cannot extend " + summarizedExtensionTargets);
            ProblemId id = ProblemId.create("extend-detached-not-allowed", "Extending a detachedConfiguration is not allowed", GradleCoreProblemGroup.configurationUsage());
            throw configurationServices.getProblems().getInternalReporter().throwing(ex, id, spec -> {
                spec.contextualLabel(ex.getMessage());
                spec.severity(Severity.ERROR);
            });
        }
    }

    public static class ConfigurationResolvableDependencies implements ResolvableDependencies {
        private final DefaultConfiguration configuration;

        @Inject
        public ConfigurationResolvableDependencies(DefaultConfiguration configuration) {
            this.configuration = configuration;
        }

        @Override
        public String getName() {
            return configuration.name;
        }

        @Override
        public String getPath() {
            return configuration.projectPath.asString();
        }

        @Override
        public String toString() {
            return "dependencies '" + configuration.identityPath + "'";
        }

        @Override
        public FileCollection getFiles() {
            return configuration.getIntrinsicFiles();
        }

        @Override
        public DependencySet getDependencies() {
            configuration.runDependencyActions();
            return configuration.getAllDependencies();
        }

        @Override
        public DependencyConstraintSet getDependencyConstraints() {
            configuration.runDependencyActions();
            return configuration.getAllDependencyConstraints();
        }

        @Override
        public void beforeResolve(Action<? super ResolvableDependencies> action) {
            configuration.dependencyResolutionListeners.add("beforeResolve", configuration.userCodeApplicationContext.reapplyCurrentLater(action));
        }

        @Override
        public void beforeResolve(Closure action) {
            beforeResolve(ConfigureUtil.configureUsing(action));
        }

        @Override
        public void afterResolve(Action<? super ResolvableDependencies> action) {
            configuration.dependencyResolutionListeners.add("afterResolve", configuration.userCodeApplicationContext.reapplyCurrentLater(action));
        }

        @Override
        public void afterResolve(Closure action) {
            afterResolve(ConfigureUtil.configureUsing(action));
        }

        @Override
        public ResolutionResult getResolutionResult() {
            configuration.assertIsResolvable();
            return new DefaultResolutionResult(configuration.resolutionAccess, configuration.configurationServices.getAttributeDesugaring());
        }

        @Override
        public ArtifactCollection getArtifacts() {
            return configuration.resolutionAccess.getPublicView().getArtifacts();
        }

        @Override
        public ArtifactView artifactView(Action<? super ArtifactView.ViewConfiguration> configAction) {
            return configuration.resolutionAccess.getPublicView().artifactView(configAction);
        }

        @Override
        public AttributeContainer getAttributes() {
            return configuration.configurationAttributes;
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
        return resolutionAccess.getHost();
    }

    private static class DefaultResolutionHost implements ResolutionHost {

        private final Path buildTreePath;
        private final DisplayName displayName;
        private final InternalProblems problems;
        private final ResolveExceptionMapper exceptionMapper;

        public DefaultResolutionHost(
            Path buildTreePath,
            DisplayName displayName,
            InternalProblems problems,
            ResolveExceptionMapper exceptionMapper
        ) {
            this.buildTreePath = buildTreePath;
            this.displayName = displayName;
            this.problems = problems;
            this.exceptionMapper = exceptionMapper;
        }

        @Override
        public InternalProblems getProblems() {
            return problems;
        }

        @Override
        public DisplayName displayName() {
            return displayName;
        }

        @Override
        public Optional<TypedResolveException> consolidateFailures(String resolutionType, Collection<Throwable> failures) {
            return Optional.ofNullable(exceptionMapper.mapFailures(failures, resolutionType, displayName));
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
            return buildTreePath.equals(that.buildTreePath);
        }

        @Override
        public int hashCode() {
            return buildTreePath.hashCode();
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

        public static String buildProperName(ProperMethodUsage usage) {
            @SuppressWarnings("deprecation")
            String capitalizedName = org.apache.commons.lang3.text.WordUtils.capitalizeFully(usage.name().replace('_', ' '));
            return capitalizedName;
        }

        public static String summarizeProperUsage(ProperMethodUsage... properUsages) {
            return Arrays.stream(properUsages)
                .map(ProperMethodUsage::buildProperName)
                .collect(Collectors.joining(", "));
        }
    }

    private static final class IllegalResolutionException extends GradleException implements ResolutionProvider {
        private final String resolution;

        public IllegalResolutionException(String message) {
            super(message);
            Documentation userGuideLink = Documentation.userManual("viewing_debugging_dependencies", "sub:resolving-unsafe-configuration-resolution-errors");
            resolution = "For more information, please refer to " + userGuideLink.getUrl() + " in the Gradle documentation.";
        }

        @Override
        public List<String> getResolutions() {
            return Collections.singletonList(resolution);
        }
    }
}
