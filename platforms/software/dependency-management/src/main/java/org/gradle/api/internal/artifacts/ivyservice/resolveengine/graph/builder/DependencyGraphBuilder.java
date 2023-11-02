/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.artifacts.ComponentSelectorConverter;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionApplicator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.Version;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.RootComponentMetadataBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.CapabilitiesConflictHandler;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.DefaultCapabilitiesConflictHandler;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.ModuleConflictHandler;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.PotentialConflict;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.CompatibilityRule;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.specs.Spec;
import org.gradle.internal.component.IncompatibleArtifactVariantsException;
import org.gradle.internal.component.ResolutionFailureHandler;
import org.gradle.internal.component.model.ComponentGraphResolveMetadata;
import org.gradle.internal.component.model.ComponentIdGenerator;
import org.gradle.internal.component.model.DefaultCompatibilityCheckResult;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.GraphVariantSelector;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.operations.BuildOperationConstraint;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class DependencyGraphBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(DependencyGraphBuilder.class);
    private final ModuleConflictHandler moduleConflictHandler;
    private final Spec<? super DependencyMetadata> edgeFilter;
    private final DependencyToComponentIdResolver idResolver;
    private final ComponentMetaDataResolver metaDataResolver;
    private final AttributesSchemaInternal attributesSchema;
    private final ModuleExclusions moduleExclusions;
    private final BuildOperationExecutor buildOperationExecutor;
    private final ComponentSelectorConverter componentSelectorConverter;
    private final DependencySubstitutionApplicator dependencySubstitutionApplicator;
    private final ImmutableAttributesFactory attributesFactory;
    private final CapabilitiesConflictHandler capabilitiesConflictHandler;
    private final AttributeDesugaring attributeDesugaring;
    private final VersionSelectorScheme versionSelectorScheme;
    private final Comparator<Version> versionComparator;
    private final ComponentIdGenerator idGenerator;
    private final VersionParser versionParser;
    private final ResolutionConflictTracker conflictTracker;
    private final GraphVariantSelector variantSelector;
    private final DocumentationRegistry documentationRegistry;

    final static Spec<EdgeState> ENDORSE_STRICT_VERSIONS_DEPENDENCY_SPEC = dependencyState -> dependencyState.getDependencyState().getDependency().isEndorsingStrictVersions();
    final static Spec<EdgeState> NOT_ENDORSE_STRICT_VERSIONS_DEPENDENCY_SPEC = dependencyState -> !dependencyState.getDependencyState().getDependency().isEndorsingStrictVersions();

    public DependencyGraphBuilder(DependencyToComponentIdResolver componentIdResolver,
                                  ComponentMetaDataResolver componentMetaDataResolver,
                                  ModuleConflictHandler moduleConflictHandler,
                                  CapabilitiesConflictHandler capabilitiesConflictHandler,
                                  Spec<? super DependencyMetadata> edgeFilter,
                                  AttributesSchemaInternal attributesSchema,
                                  ModuleExclusions moduleExclusions,
                                  BuildOperationExecutor buildOperationExecutor,
                                  DependencySubstitutionApplicator dependencySubstitutionApplicator,
                                  ComponentSelectorConverter componentSelectorConverter,
                                  ImmutableAttributesFactory attributesFactory,
                                  AttributeDesugaring attributeDesugaring,
                                  VersionSelectorScheme versionSelectorScheme,
                                  Comparator<Version> versionComparator,
                                  ComponentIdGenerator idGenerator,
                                  VersionParser versionParser,
                                  GraphVariantSelector variantSelector,
                                  DocumentationRegistry documentationRegistry
    ) {
        this.idResolver = componentIdResolver;
        this.metaDataResolver = componentMetaDataResolver;
        this.moduleConflictHandler = moduleConflictHandler;
        this.edgeFilter = edgeFilter;
        this.attributesSchema = attributesSchema;
        this.moduleExclusions = moduleExclusions;
        this.buildOperationExecutor = buildOperationExecutor;
        this.dependencySubstitutionApplicator = dependencySubstitutionApplicator;
        this.componentSelectorConverter = componentSelectorConverter;
        this.attributesFactory = attributesFactory;
        this.capabilitiesConflictHandler = capabilitiesConflictHandler;
        this.attributeDesugaring = attributeDesugaring;
        this.versionSelectorScheme = versionSelectorScheme;
        this.versionComparator = versionComparator;
        this.idGenerator = idGenerator;
        this.versionParser = versionParser;
        this.conflictTracker = new ResolutionConflictTracker(moduleConflictHandler, capabilitiesConflictHandler);
        this.variantSelector = variantSelector;
        this.documentationRegistry = documentationRegistry;
    }

    public void resolve(
        RootComponentMetadataBuilder.RootComponentState rootComponent,
        ResolutionStrategyInternal resolutionStrategy,
        List<? extends DependencyMetadata> syntheticDependencies,
        final DependencyGraphVisitor modelVisitor
    ) {
        ResolveState resolveState = new ResolveState(
            idGenerator,
            rootComponent,
            idResolver,
            metaDataResolver,
            edgeFilter,
            attributesSchema,
            moduleExclusions,
            componentSelectorConverter,
            attributesFactory,
            attributeDesugaring,
            dependencySubstitutionApplicator,
            versionSelectorScheme,
            versionComparator,
            versionParser,
            moduleConflictHandler.getResolver(),
            resolutionStrategy.getConflictResolution(),
            syntheticDependencies,
            conflictTracker,
            variantSelector
        );

        traverseGraph(resolveState);

        validateGraph(resolveState, resolutionStrategy.isFailingOnDynamicVersions(), resolutionStrategy.isFailingOnChangingVersions());

        assembleResult(resolveState, modelVisitor);
    }

    /**
     * Traverses the dependency graph, resolving conflicts and building the paths from the root configuration.
     */
    private void traverseGraph(final ResolveState resolveState) {
        resolveState.onMoreSelected(resolveState.getRoot());
        final List<EdgeState> dependencies = Lists.newArrayList();

        while (resolveState.peek() != null || moduleConflictHandler.hasConflicts() || capabilitiesConflictHandler.hasConflicts()) {
            if (resolveState.peek() != null) {
                final NodeState node = resolveState.pop();
                LOGGER.debug("Visiting configuration {}.", node);

                // Register capabilities for this node
                registerCapabilities(resolveState, node);

                // Initialize and collect any new outgoing edges of this node
                dependencies.clear();
                node.visitOutgoingDependencies(dependencies);
                boolean edgeWasProcessed = resolveEdges(node, dependencies, ENDORSE_STRICT_VERSIONS_DEPENDENCY_SPEC, false, resolveState);
                node.collectEndorsedStrictVersions(dependencies);
                resolveEdges(node, dependencies, NOT_ENDORSE_STRICT_VERSIONS_DEPENDENCY_SPEC, edgeWasProcessed, resolveState);
            } else {
                // We have some batched up conflicts. Resolve the first, and continue traversing the graph
                if (moduleConflictHandler.hasConflicts()) {
                    moduleConflictHandler.resolveNextConflict(resolveState.getReplaceSelectionWithConflictResultAction());
                } else {
                    capabilitiesConflictHandler.resolveNextConflict(resolveState.getReplaceSelectionWithConflictResultAction());
                }
            }

        }
    }

    private void registerCapabilities(final ResolveState resolveState, final NodeState node) {
        node.forEachCapability(capabilitiesConflictHandler, new Action<Capability>() {
            @Override
            public void execute(Capability capability) {
                // This is a performance optimization. Most modules do not declare capabilities. So, instead of systematically registering
                // an implicit capability for each module that we see, we only consider modules which _declare_ capabilities. If they do,
                // then we try to find a module which provides the same capability. It that module has been found, then we register it.
                // Otherwise, we have nothing to do. This avoids most of registrations.
                Collection<NodeState> implicitProvidersForCapability = Collections.emptyList();
                for (ModuleResolveState state : resolveState.getModules()) {
                    if (state.getId().getGroup().equals(capability.getGroup()) && state.getId().getName().equals(capability.getName())) {
                        Collection<ComponentState> versions = state.getVersions();
                        implicitProvidersForCapability = Lists.newArrayListWithExpectedSize(versions.size());
                        for (ComponentState version : versions) {
                            List<NodeState> nodes = version.getNodes();
                            for (NodeState nodeState : nodes) {
                                // Collect nodes as implicit capability providers if different than current node, selected and not having explicit capabilities
                                if (node != nodeState && nodeState.isSelected() && doesNotDeclareExplicitCapability(nodeState)) {
                                    implicitProvidersForCapability.add(nodeState);
                                }
                            }
                        }
                        break;
                    }
                }
                PotentialConflict c = capabilitiesConflictHandler.registerCandidate(
                    DefaultCapabilitiesConflictHandler.candidate(node, capability, implicitProvidersForCapability)
                );
                if (c.conflictExists()) {
                    c.withParticipatingModules(resolveState.getDeselectVersionAction());
                }
            }

            private boolean doesNotDeclareExplicitCapability(NodeState nodeState) {
                return nodeState.getMetadata().getCapabilities().getCapabilities().isEmpty();
            }
        });
    }

    private boolean resolveEdges(
        final NodeState node,
        final List<EdgeState> dependencies,
        final Spec<EdgeState> dependencyFilter,
        final boolean recomputeSelectors,
        final ResolveState resolveState) {
        if (dependencies.isEmpty()) {
            return false;
        }
        if (performSelectionSerially(dependencies, dependencyFilter, resolveState, recomputeSelectors)) {
            maybeDownloadMetadataInParallel(node, dependencies, dependencyFilter);
            attachToTargetRevisionsSerially(dependencies, dependencyFilter);
            return true;
        } else {
            return false;
        }

    }

    private boolean performSelectionSerially(List<EdgeState> dependencies, Spec<EdgeState> dependencyFilter, ResolveState resolveState, boolean recomputeSelectors) {
        boolean processed = false;
        for (EdgeState dependency : dependencies) {
            if (!dependencyFilter.isSatisfiedBy(dependency)) {
                continue;
            }
            if (recomputeSelectors) {
                dependency.computeSelector();
            }
            SelectorState selector = dependency.getSelector();
            ModuleResolveState module = selector.getTargetModule();

            if (selector.canResolve() && module.getSelectors().size() > 0) {
                // Have an unprocessed/new selector for this module. Need to re-select the target version (if there are any selectors that can be used).
                performSelection(resolveState, module);
            }
            if (dependency.isUsed()) {
                // Some corner case result in the edge being removed, in that case it needs to be "removed"
                module.addUnattachedDependency(dependency);
            }
            processed = true;
        }
        return processed;
    }

    /**
     * Attempts to resolve a target `ComponentState` for the given dependency.
     * On successful resolve, a `ComponentState` is constructed for the identifier, recorded as {@link ModuleResolveState#getSelected()},
     * and added to the graph.
     * On resolve failure, the failure is recorded and no `ComponentState` is selected.
     */
    private void performSelection(ResolveState resolveState, ModuleResolveState module) {
        ComponentState currentSelection = module.getSelected();

        try {
            module.maybeUpdateSelection(conflictTracker);
        } catch (ModuleVersionResolveException e) {
            // Ignore: All selectors failed, and will have failures recorded
            return;
        }

        // If no current selection for module, just use the candidate.
        if (currentSelection == null) {
            // This is the first time we've seen the module, so register with conflict resolver.
            checkForModuleConflicts(resolveState, module);
        }
    }

    private void checkForModuleConflicts(ResolveState resolveState, ModuleResolveState module) {
        // A new module. Check for conflict with capabilities and module replacements.
        PotentialConflict c = moduleConflictHandler.registerCandidate(module);
        if (c.conflictExists()) {
            // We have a conflict
            LOGGER.debug("Found new conflicting module {}", module);

            // For each module participating in the conflict, deselect the currently selection, and remove all outgoing edges from the version.
            // This will propagate through the graph and prune configurations that are no longer required.
            c.withParticipatingModules(resolveState.getDeselectVersionAction());
        }
    }

    /**
     * Prepares the resolution of edges, either serially or concurrently.
     * It uses a simple heuristic to determine if we should perform concurrent resolution, based on the number of edges, and whether they have unresolved metadata.
     */
    private void maybeDownloadMetadataInParallel(NodeState node, List<EdgeState> dependencies, Spec<EdgeState> dependencyFilter) {
        List<ComponentState> requiringDownload = null;
        for (EdgeState dependency : dependencies) {
            if (!dependencyFilter.isSatisfiedBy(dependency)) {
                continue;
            }
            ComponentState targetComponent = dependency.getTargetComponent();
            if (targetComponent != null && targetComponent.isSelected() && !targetComponent.alreadyResolved()) {
                if (!metaDataResolver.isFetchingMetadataCheap(targetComponent.getComponentId())) {
                    // Avoid initializing the list if there are no components requiring download (a common case)
                    if (requiringDownload == null) {
                        requiringDownload = Lists.newArrayList();
                    }
                    requiringDownload.add(targetComponent);
                }
            }
        }
        // Only download in parallel if there is more than 1 component to download
        if (requiringDownload != null && requiringDownload.size() > 1) {
            final ImmutableList<ComponentState> toDownloadInParallel = ImmutableList.copyOf(requiringDownload);
            LOGGER.debug("Submitting {} metadata files to resolve in parallel for {}", toDownloadInParallel.size(), node);
            buildOperationExecutor.runAll(buildOperationQueue -> {
                for (final ComponentState componentState : toDownloadInParallel) {
                    buildOperationQueue.add(new DownloadMetadataOperation(componentState));
                }
            }, BuildOperationConstraint.UNCONSTRAINED);
        }
    }

    private void attachToTargetRevisionsSerially(List<EdgeState> dependencies, Spec<EdgeState> dependencyFilter) {
        // the following only needs to be done serially to preserve ordering of dependencies in the graph: we have visited the edges
        // but we still didn't add the result to the queue. Doing it from resolve threads would result in non-reproducible graphs, where
        // edges could be added in different order. To avoid this, the addition of new edges is done serially.
        for (EdgeState dependency : dependencies) {
            if (dependencyFilter.isSatisfiedBy(dependency)) {
                dependency.attachToTargetConfigurations();
            }
        }
    }

    private void validateGraph(ResolveState resolveState, boolean denyDynamicSelectors, boolean denyChangingModules) {
        for (ModuleResolveState module : resolveState.getModules()) {
            ComponentState selected = module.getSelected();
            if (selected != null) {
                ResolutionFailureHandler resolutionFailureHandler = resolveState.getVariantSelector().getFailureProcessor();
                if (selected.isRejected()) {
                    GradleException error = new GradleException(selected.getRejectedErrorMessage());
                    attachFailureToEdges(error, module.getIncomingEdges());
                    // We need to attach failures on unattached dependencies too, in case a node wasn't selected
                    // at all, but we still want to see an error message for it.
                    attachFailureToEdges(error, module.getUnattachedDependencies());
                } else {
                    if (module.isVirtualPlatform()) {
                        attachMultipleForceOnPlatformFailureToEdges(module);
                    } else if (selected.hasMoreThanOneSelectedNodeUsingVariantAwareResolution()) {
                        validateMultipleNodeSelection(module, selected, resolutionFailureHandler);
                    }
                    if (denyDynamicSelectors) {
                        validateDynamicSelectors(selected);
                    }
                    if (denyChangingModules) {
                        validateChangingVersions(selected);
                    }
                }
            } else if (module.isVirtualPlatform()) {
                attachMultipleForceOnPlatformFailureToEdges(module);
            }
        }
        List<EdgeState> incomingRootEdges = resolveState.getRoot().getIncomingEdges();
        if (!incomingRootEdges.isEmpty()) {
            String rootNodeName = resolveState.getRoot().getResolvedConfigurationId().getConfiguration();
            DeprecationLogger.deprecate(
                    String.format(
                        "While resolving configuration '%s', it was also selected as a variant. Configurations should not act as both a resolution root and a variant simultaneously. " +
                            "Depending on the resolved configuration in this manner",
                        rootNodeName
                    ))
                .withAdvice("Be sure to mark configurations meant for resolution as canBeConsumed=false or use the 'resolvable(String)' configuration factory method to create them.")
                .willBecomeAnErrorInGradle9()
                .withUpgradeGuideSection(8, "depending_on_root_configuration")
                .nagUser();
        }
    }

    private static boolean isDynamic(SelectorState selector) {
        ResolvedVersionConstraint versionConstraint = selector.getVersionConstraint();
        if (versionConstraint != null) {
            return versionConstraint.isDynamic();
        }
        return false;
    }

    private void validateDynamicSelectors(ComponentState selected) {
        List<SelectorState> selectors = ImmutableList.copyOf(selected.getModule().getSelectors());
        if (!selectors.isEmpty()) {
            if (selectors.stream().allMatch(DependencyGraphBuilder::isDynamic)) {
                // when all selectors are dynamic, result is undoubtedly unstable
                markDeniedDynamicVersions(selected);
            } else if (selectors.stream().anyMatch(DependencyGraphBuilder::isDynamic)) {
                checkIfDynamicVersionAllowed(selected, selectors);
            }
        }
    }

    private void checkIfDynamicVersionAllowed(ComponentState selected, List<SelectorState> selectors) {
        String version = selected.getId().getVersion();
        // There must be at least one non dynamic selector agreeing with the selection
        // for the resolution result to be stable
        // and for dynamic selectors, only the "stable" ones work, which is currently
        // only ranges because those are the only ones which accept a selection without
        // upgrading
        boolean accept = false;
        for (SelectorState selector : selectors) {
            ResolvedVersionConstraint versionConstraint = selector.getVersionConstraint();
            if (!versionConstraint.isDynamic()) {
                // this selector is not dynamic, let's see if it agrees with the selection
                if (versionConstraint.accepts(version)) {
                    accept = true;
                }
            } else if (!versionConstraint.canBeStable()) {
                accept = false;
                break;
            }
        }
        if (!accept) {
            markDeniedDynamicVersions(selected);
        }
    }

    private void markDeniedDynamicVersions(ComponentState cs) {
        for (NodeState node : cs.getNodes()) {
            List<EdgeState> incomingEdges = node.getIncomingEdges();
            for (EdgeState incomingEdge : incomingEdges) {
                ComponentSelector selector = incomingEdge.getSelector().getSelector();
                incomingEdge.failWith(new ModuleVersionResolveException(selector, () ->
                    String.format("Could not resolve %s: Resolution strategy disallows usage of dynamic versions", selector)));
            }
        }
    }

    private void validateChangingVersions(ComponentState selected) {
        ComponentGraphResolveMetadata metadata = selected.getMetadataOrNull();
        boolean moduleIsChanging = metadata != null && metadata.isChanging();
        for (NodeState node : selected.getNodes()) {
            List<EdgeState> incomingEdges = node.getIncomingEdges();
            for (EdgeState incomingEdge : incomingEdges) {
                if (moduleIsChanging || incomingEdge.getDependencyMetadata().isChanging()) {
                    ComponentSelector selector = incomingEdge.getSelector().getSelector();
                    incomingEdge.failWith(new ModuleVersionResolveException(selector, () ->
                        String.format("Could not resolve %s: Resolution strategy disallows usage of changing versions", selector)));
                }
            }
        }
    }

    /**
     * Validates that all selected nodes of a single component have compatible attributes,
     * when using variant aware resolution.
     */
    private void validateMultipleNodeSelection(ModuleResolveState module, ComponentState selected, ResolutionFailureHandler resolutionFailureHandler) {
        Set<NodeState> selectedNodes = selected.getNodes().stream()
            .filter(n -> n.isSelected() && !n.isAttachedToVirtualPlatform() && !n.hasShadowedCapability())
            .collect(Collectors.toSet());
        if (selectedNodes.size() < 2) {
            return;
        }
        Set<Set<NodeState>> combinations = Sets.combinations(selectedNodes, 2);
        Set<NodeState> incompatibleNodes = Sets.newHashSet();
        for (Set<NodeState> combination : combinations) {
            Iterator<NodeState> it = combination.iterator();
            NodeState first = it.next();
            NodeState second = it.next();
            assertCompatibleAttributes(first, second, incompatibleNodes);
        }
        if (!incompatibleNodes.isEmpty()) {
            IncompatibleArtifactVariantsException variantsSelectionException = resolutionFailureHandler.incompatibleArtifactVariantsFailure(selected, incompatibleNodes);
            for (EdgeState edge : module.getIncomingEdges()) {
                edge.failWith(variantsSelectionException);
            }
        }
    }

    private void assertCompatibleAttributes(NodeState first, NodeState second, Set<NodeState> incompatibleNodes) {
        ImmutableAttributes firstAttributes = first.getMetadata().getAttributes();
        ImmutableAttributes secondAttributes = second.getMetadata().getAttributes();
        ImmutableSet<Attribute<?>> firstKeys = firstAttributes.keySet();
        ImmutableSet<Attribute<?>> secondKeys = secondAttributes.keySet();
        for (Attribute<?> attribute : Sets.intersection(firstKeys, secondKeys)) {
            CompatibilityRule<Object> rule = attributesSchema.compatibilityRules(attribute);
            Object v1 = firstAttributes.getAttribute(attribute);
            Object v2 = secondAttributes.getAttribute(attribute);
            // for all commons attributes, make sure they are compatible with each other
            if (!compatible(rule, v1, v2) && !compatible(rule, v2, v1)) {
                incompatibleNodes.add(first);
                incompatibleNodes.add(second);
            }
        }
    }

    private static boolean compatible(CompatibilityRule<Object> rule, @Nullable Object v1, @Nullable Object v2) {
        if (Objects.equals(v1, v2)) {
            // Equal values are compatible
            return true;
        }
        DefaultCompatibilityCheckResult<Object> result = new DefaultCompatibilityCheckResult<>(v1, v2);
        rule.execute(result);
        return result.hasResult() && result.isCompatible();
    }

    private void attachMultipleForceOnPlatformFailureToEdges(ModuleResolveState module) {
        List<EdgeState> forcedEdges = null;
        boolean hasMultipleVersions = false;
        String currentVersion = module.maybeFindForcedPlatformVersion();
        Set<ModuleResolveState> participatingModules = module.getPlatformState().getParticipatingModules();
        for (ModuleResolveState participatingModule : participatingModules) {
            for (EdgeState incomingEdge : participatingModule.getIncomingEdges()) {
                SelectorState selector = incomingEdge.getSelector();
                if (isPlatformForcedEdge(selector)) {
                    ComponentSelector componentSelector = selector.getSelector();
                    if (componentSelector instanceof ModuleComponentSelector) {
                        ModuleComponentSelector mcs = (ModuleComponentSelector) componentSelector;
                        if (!incomingEdge.getFrom().getComponent().getModule().equals(module)) {
                            if (forcedEdges == null) {
                                forcedEdges = Lists.newArrayList();
                            }
                            forcedEdges.add(incomingEdge);
                            if (currentVersion == null) {
                                currentVersion = mcs.getVersion();
                            } else {
                                if (!currentVersion.equals(mcs.getVersion())) {
                                    hasMultipleVersions = true;
                                }
                            }
                        }
                    }
                }
            }
        }
        if (hasMultipleVersions) {
            attachFailureToEdges(new GradleException("Multiple forces on different versions for virtual platform " + module.getId()), forcedEdges);
        }
    }

    private static boolean isPlatformForcedEdge(SelectorState selector) {
        return selector.isForce() && !selector.isSoftForce();
    }

    /**
     * Attaches errors late in the process. This is useful whenever we have built a graph, and that
     * validation is going to cause a failure (the error is not in the graph itself, but in the way
     * we handle it: do we use failOnVersionConflict?). This method therefore needs to be called
     * before the graph is handed over, so that we can properly fail resolution.
     */
    private void attachFailureToEdges(GradleException error, Collection<EdgeState> incomingEdges) {
        for (EdgeState edge : incomingEdges) {
            edge.failWith(error);
        }
    }

    /**
     * Populates the result from the graph traversal state.
     */
    private void assembleResult(ResolveState resolveState, DependencyGraphVisitor visitor) {
        visitor.start(resolveState.getRoot());

        // Visit the selectors
        for (DependencyGraphSelector selector : resolveState.getSelectors()) {
            visitor.visitSelector(selector);
        }

        // Visit the nodes prior to visiting the edges
        for (NodeState nodeState : resolveState.getNodes()) {
            if (nodeState.shouldIncludedInGraphResult()) {
                visitor.visitNode(nodeState);
            }
        }

        // Collect the components to sort in consumer-first order
        LinkedList<ComponentState> queue = Lists.newLinkedList();
        for (ModuleResolveState module : resolveState.getModules()) {
            if (module.getSelected() != null && !module.isVirtualPlatform()) {
                queue.add(module.getSelected());
            }
        }

        // Visit the edges after sorting the components in consumer-first order
        while (!queue.isEmpty()) {
            ComponentState component = queue.peekFirst();
            if (component.getVisitState() == VisitState.NotSeen) {
                component.setVisitState(VisitState.Visiting);
                int pos = 0;
                for (NodeState node : component.getNodes()) {
                    if (!node.isSelected()) {
                        continue;
                    }
                    for (EdgeState edge : node.getIncomingEdges()) {
                        ComponentState owner = edge.getFrom().getOwner();
                        if (owner.getVisitState() == VisitState.NotSeen && !owner.getModule().isVirtualPlatform()) {
                            queue.add(pos, owner);
                            pos++;
                        } // else, already visited or currently visiting (which means a cycle), skip
                    }
                }
                if (pos == 0) {
                    // have visited all consumers, so visit this node
                    component.setVisitState(VisitState.Visited);
                    queue.removeFirst();
                    for (NodeState node : component.getNodes()) {
                        if (node.isSelected()) {
                            visitor.visitEdges(node);
                        }
                    }
                }
            } else if (component.getVisitState() == VisitState.Visiting) {
                // have visited all consumers, so visit this node
                component.setVisitState(VisitState.Visited);
                queue.removeFirst();
                for (NodeState node : component.getNodes()) {
                    if (node.isSelected()) {
                        visitor.visitEdges(node);
                    }
                }
            } else {
                // else, already visited previously, skip
                queue.removeFirst();
            }
        }

        visitor.finish(resolveState.getRoot());
    }

    enum VisitState {
        NotSeen, Visiting, Visited
    }

}
