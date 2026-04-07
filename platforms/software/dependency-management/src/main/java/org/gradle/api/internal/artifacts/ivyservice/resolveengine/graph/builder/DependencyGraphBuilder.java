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
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.ComponentSelectorConverter;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.configurations.ConflictResolution;
import org.gradle.api.internal.artifacts.dsl.ImmutableModuleReplacements;
import org.gradle.api.internal.artifacts.ivyservice.ResolutionParameters;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionApplicator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.CapabilitiesResolutionInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleConflictResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.CapabilitiesConflictHandler;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.Conflict;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.ModuleConflictHandler;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.VersionConflictException;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.AttributeSchemaServices;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.api.internal.attributes.matching.AttributeMatcher;
import org.gradle.api.specs.Spec;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState;
import org.gradle.internal.component.local.model.LocalVariantGraphResolveState;
import org.gradle.internal.component.model.ComponentGraphResolveMetadata;
import org.gradle.internal.component.model.ComponentIdGenerator;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.GraphVariantSelector;
import org.gradle.internal.component.model.VariantGraphResolveMetadata;
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler;
import org.gradle.internal.component.resolution.failure.exception.AbstractResolutionFailureException;
import org.gradle.internal.operations.BuildOperationConstraint;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ServiceScope(Scope.Project.class)
public class DependencyGraphBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(DependencyGraphBuilder.class);

    private final ModuleExclusions moduleExclusions;
    private final AttributesFactory attributesFactory;
    private final AttributeSchemaServices attributeSchemaServices;
    private final AttributeDesugaring attributeDesugaring;
    private final VersionSelectorScheme versionSelectorScheme;
    private final VersionComparator versionComparator;
    private final ComponentIdGenerator idGenerator;
    private final VersionParser versionParser;
    private final GraphVariantSelector variantSelector;
    private final BuildOperationExecutor buildOperationExecutor;

    @Inject
    public DependencyGraphBuilder(
        ModuleExclusions moduleExclusions,
        AttributesFactory attributesFactory,
        AttributeSchemaServices attributeSchemaServices,
        AttributeDesugaring attributeDesugaring,
        VersionSelectorScheme versionSelectorScheme,
        VersionComparator versionComparator,
        ComponentIdGenerator idGenerator,
        VersionParser versionParser,
        GraphVariantSelector variantSelector,
        BuildOperationExecutor buildOperationExecutor
    ) {
        this.moduleExclusions = moduleExclusions;
        this.attributesFactory = attributesFactory;
        this.attributeSchemaServices = attributeSchemaServices;
        this.attributeDesugaring = attributeDesugaring;
        this.versionSelectorScheme = versionSelectorScheme;
        this.versionComparator = versionComparator;
        this.idGenerator = idGenerator;
        this.versionParser = versionParser;
        this.variantSelector = variantSelector;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    public void resolve(
        LocalComponentGraphResolveState rootComponent,
        LocalVariantGraphResolveState rootVariant,
        List<? extends DependencyMetadata> syntheticDependencies,
        Spec<? super DependencyMetadata> edgeFilter,
        ComponentSelectorConverter componentSelectorConverter,
        DependencyToComponentIdResolver componentIdResolver,
        ComponentMetaDataResolver componentMetaDataResolver,
        ImmutableModuleReplacements moduleReplacements,
        DependencySubstitutionApplicator dependencySubstitutionApplicator,
        ModuleConflictResolver<ComponentState> moduleConflictResolver,
        ImmutableList<CapabilitiesResolutionInternal.CapabilityResolutionRule> capabilityResolutionRules,
        ConflictResolution conflictResolution,
        boolean failingOnDynamicVersions,
        boolean failingOnChangingVersions,
        ResolutionParameters.FailureResolutions failureResolutions,
        DependencyGraphVisitor modelVisitor
    ) {
        ResolveState resolveState = new ResolveState(
            idGenerator,
            rootComponent,
            rootVariant,
            componentIdResolver,
            componentMetaDataResolver,
            edgeFilter,
            moduleExclusions,
            componentSelectorConverter,
            attributesFactory,
            attributeSchemaServices,
            attributeDesugaring,
            dependencySubstitutionApplicator,
            versionSelectorScheme,
            versionComparator,
            versionParser,
            conflictResolution,
            syntheticDependencies,
            moduleConflictResolver,
            moduleReplacements,
            capabilityResolutionRules,
            variantSelector
        );

        traverseGraph(resolveState);

        validateGraph(resolveState, failingOnDynamicVersions, failingOnChangingVersions, conflictResolution, failureResolutions);

        assembleResult(resolveState, modelVisitor);
    }

    /**
     * Traverses the dependency graph, resolving conflicts and building the paths from the root configuration.
     *
     * <p>The loop is structured around a strict priority order to maximize download batching:
     * <ol>
     *   <li><b>Node processing</b> — drain the node queue one at a time. Discovers outgoing edges
     *       and registers their target modules for selection. Also cleans up nodes that have lost
     *       all incoming edges.</li>
     *   <li><b>Attachment</b> — attach edges for modules whose selected component already has
     *       resolved metadata. This discovers new nodes (which feed back to step 1) without any IO.</li>
     *   <li><b>Selection</b> — pick the best version for one pending module. If selection changes
     *       or the selected component already has metadata, return to step 1 immediately.
     *       Otherwise, the module needs a metadata download — continue selecting the next module.</li>
     *   <li><b>Download</b> — all cheap work is exhausted. Batch-download metadata for every
     *       module that still needs it, then return to step 1.</li>
     *   <li><b>Conflict resolution</b> — only when all queues are empty. Resolve one conflict
     *       and return to step 1.</li>
     * </ol>
     */
    private void traverseGraph(final ResolveState resolveState) {
        resolveState.onMoreSelected(resolveState.getRoot());

        List<EdgeState> edges = new ArrayList<>();
        List<ComponentState> componentsToDownload = new ArrayList<>();

        ModuleConflictHandler moduleConflictHandler = resolveState.getModuleConflictHandler();
        CapabilitiesConflictHandler capabilitiesConflictHandler = resolveState.getCapabilitiesConflictHandler();

        while (true) {
            // Priority 1: Process nodes that may have lost incoming edges (removals).
            // These nodes likely need their outgoing edges removed, which releases selectors
            // and reduces work for attachment and selection.
            if (resolveState.hasNextRemoval()) {
                NodeState node = resolveState.nextRemoval();
                if (node.doesNotContributeToGraph()) {
                    node.removeOutgoingEdges();
                } else {
                    processNodeAddition(node, edges, resolveState, capabilitiesConflictHandler);
                }
                continue;
            }

            // Priority 2: Attach edges for modules with resolved metadata.
            // This discovers new nodes (additions) without IO.
            if (attachModulesWithResolvedMetadata(resolveState)) {
                continue;
            }

            // Priority 3: Process nodes that gained incoming edges (additions).
            // Deferred until after attachment so that nodes accumulate all incoming edges
            // before recomputing inherited state (excludes, strict versions).
            if (resolveState.hasNextAddition()) {
                NodeState node = resolveState.nextAddition();
                if (node.doesNotContributeToGraph()) {
                    node.removeOutgoingEdges();
                } else {
                    processNodeAddition(node, edges, resolveState, capabilitiesConflictHandler);
                }
                continue;
            }

            // Priority 4: Select the next pending module
            if (resolveState.hasNextSelection()) {
                selectUntilActionable(resolveState);
                continue;
            }

            // Priority 5: Download all pending metadata in one batch
            if (downloadPendingMetadata(resolveState, componentsToDownload)) {
                continue;
            }

            // Priority 6: Resolve conflicts
            if (moduleConflictHandler.hasConflicts()) {
                moduleConflictHandler.resolveNextConflict();
                continue;
            }
            if (capabilitiesConflictHandler.hasConflicts()) {
                capabilitiesConflictHandler.resolveNextConflict();
                continue;
            }

            // All queues empty, no conflicts remaining — done.
            break;
        }
    }

    /**
     * Processes a node that contributes to the graph: checks for capability conflicts,
     * then visits its outgoing dependencies and registers their target modules.
     */
    private static void processNodeAddition(
        NodeState node,
        List<EdgeState> edges,
        ResolveState resolveState,
        CapabilitiesConflictHandler capabilitiesConflictHandler
    ) {
        if (capabilitiesConflictHandler.registerCandidate(node)) {
            return;
        }

        edges.clear();
        node.visitOutgoingDependenciesAndCollectEdges(edges);
        for (EdgeState edge : edges) {
            SelectorState selector = edge.getSelector();
            ModuleResolveState targetModule = selector.getTargetModule();
            if (selector.canAffectSelection()) {
                resolveState.enqueueForSelection(targetModule);
            } else if (!targetModule.isQueuedForSelection()) {
                resolveState.enqueueForAttachment(targetModule);
            }
        }
    }

    /**
     * Iterates the attachment queue, attaching edges for modules whose selected component
     * already has resolved metadata (or whose metadata is cheap to fetch synchronously).
     * Modules that still need a download remain in the queue for later.
     *
     * @return true if any attachment was performed (meaning new nodes may be on the queue)
     */
    private static boolean attachModulesWithResolvedMetadata(ResolveState resolveState) {
        boolean didWork = false;
        while (resolveState.hasNextAttachment()) {
            ModuleResolveState module = resolveState.nextAttachment();
            if (module.isInModuleConflict() || module.getUnattachedEdges().isEmpty()) {
                continue;
            }
            ComponentState selected = module.getSelected();
            if (selected == null) {
                continue;
            }
            if (!selected.alreadyResolved()) {
                if (selected.isMetadataCheapToFetch()) {
                    selected.getMetadataOrNull();
                } else {
                    resolveState.enqueueForDownload(module);
                    continue;
                }
            }
            module.attachUnattachedEdges();
            didWork = true;
        }
        return didWork;
    }

    /**
     * Pops modules from the selection queue one at a time and performs selection.
     * Stops early and returns when:
     * <ul>
     *   <li>Selection changed (old nodes were enqueued for cleanup)</li>
     *   <li>The selected component already has metadata (can be attached immediately)</li>
     *   <li>The selection queue is empty</li>
     * </ul>
     * Modules that need a metadata download are enqueued for attachment but
     * selection continues to the next module without returning.
     */
    @SuppressWarnings("ReferenceEquality")
    private void selectUntilActionable(ResolveState resolveState) {
        while (resolveState.hasNextSelection()) {
            ModuleResolveState module = resolveState.nextSelection();

            if (module.getSelectors().size() == 0) {
                continue;
            }

            ComponentState previousSelection = module.getSelected();
            performSelection(resolveState, module);
            ComponentState newSelection = module.getSelected();

            if (newSelection == null) {
                continue; // Selection failed
            }

            resolveState.enqueueForAttachment(newSelection.getModule());

            if (previousSelection != null && newSelection != previousSelection) {
                // Selection changed — old nodes were enqueued. Return to drain them.
                return;
            }

            if (newSelection.alreadyResolved()) {
                // Metadata already available — return to attach immediately.
                return;
            }
        }
    }

    /**
     * Collects all modules in the attachment queue that still need a metadata download
     * and downloads them in parallel.
     *
     * @return true if any downloads were performed
     */
    private boolean downloadPendingMetadata(
        ResolveState resolveState,
        List<ComponentState> componentsToDownload
    ) {
        componentsToDownload.clear();
        while (resolveState.hasNextDownload()) {
            ModuleResolveState module = resolveState.nextDownload();
            ComponentState selected = module.getSelected();
            if (selected != null && !selected.alreadyResolved() && !module.isInModuleConflict()) {
                componentsToDownload.add(selected);
            }
        }
        if (componentsToDownload.isEmpty()) {
            return false;
        }
        downloadAllComponents(componentsToDownload, buildOperationExecutor);
        for (ComponentState component : componentsToDownload) {
            resolveState.enqueueForAttachment(component.getModule());
        }
        return true;
    }

    /**
     * Attempts to resolve a target `ComponentState` for the given dependency.
     * On successful resolve, a `ComponentState` is constructed for the identifier, recorded as {@link ModuleResolveState#getSelected()},
     * and added to the graph.
     * On resolve failure, the failure is recorded and no `ComponentState` is selected.
     */
    private static void performSelection(ResolveState resolveState, ModuleResolveState module) {
        ComponentState currentSelection = module.getSelected();

        try {
            module.maybeUpdateSelection();
        } catch (ModuleVersionResolveException e) {
            // Ignore: All selectors failed, and will have failures recorded
            return;
        }

        // If no current selection for module, just use the candidate.
        if (currentSelection == null) {
            // This is the first time we've seen the module, so register with conflict resolver.
            resolveState.getModuleConflictHandler().registerCandidate(module);
        }
    }

    private static void downloadAllComponents(List<ComponentState> requiringDownload, BuildOperationExecutor buildOperationExecutor) {
        if (requiringDownload.size() == 1) {
            // Only one thing to download. No need to start any threads. Download synchronously.
            ComponentState component = requiringDownload.get(0);
            component.getMetadataOrNull();
        } else if (requiringDownload.size() > 1) {
            LOGGER.debug("Submitting {} metadata files to resolve in parallel", requiringDownload.size());
            buildOperationExecutor.runAll(buildOperationQueue -> {
                for (final ComponentState componentState : requiringDownload) {
                    buildOperationQueue.add(new DownloadMetadataOperation(componentState));
                }
            }, BuildOperationConstraint.UNCONSTRAINED);
        }
    }

    private static void validateGraph(
        ResolveState resolveState,
        boolean denyDynamicSelectors,
        boolean denyChangingModules,
        ConflictResolution conflictResolution,
        ResolutionParameters.FailureResolutions failureResolutions
    ) {
        assertHasValidGraphStructure(resolveState);

        ImmutableAttributesSchema consumerSchema = resolveState.getConsumerSchema();
        for (ModuleResolveState module : resolveState.getModules()) {
            ComponentState selected = module.getSelected();
            if (selected != null) {
                ResolutionFailureHandler resolutionFailureHandler = resolveState.getVariantSelector().getFailureHandler();
                if (selected.isRejected()) {
                    List<String> conflictResolutions = buildConflictResolutions(selected, failureResolutions).getRight();
                    GradleException error = resolutionFailureHandler.componentRejected(selected, conflictResolutions);
                    // We need to attach failures on unattached dependencies too, in case a node wasn't selected
                    // at all, but we still want to see an error message for it.
                    module.visitAllIncomingEdges(edge -> edge.failWith(error));
                } else if (Iterables.any(selected.getNodes(), node -> node.getReplacement() == null)) {
                    for (NodeState node : selected.getNodes()) {
                        if (node.isRejectedForCapabilityConflict()) {
                            GradleException error = resolutionFailureHandler.nodeRejected(node);
                            node.getIncomingEdges().forEach(edge -> edge.failWith(error));
                        }
                    }
                    if (module.isVirtualPlatform()) {
                        attachMultipleForceOnPlatformFailureToEdges(module);
                    } else if (selected.hasMoreThanOneSelectedNodeUsingVariantAwareResolution()) {
                        validateMultipleNodeSelection(consumerSchema, module, selected, resolutionFailureHandler, resolveState.getAttributeSchemaServices());
                    }
                    if (denyDynamicSelectors) {
                        validateDynamicSelectors(selected);
                    }
                    if (denyChangingModules) {
                        validateChangingVersions(selected);
                    }
                    if (conflictResolution == ConflictResolution.strict) {
                        validateVersionConflicts(selected, failureResolutions);
                    }
                }
            } else if (module.isVirtualPlatform()) {
                attachMultipleForceOnPlatformFailureToEdges(module);
            }
        }
    }

    /**
     * Tests for fundamentally broken graphs. Only enabled when assertions are enabled,
     * as we do not expect any user-constructed graphs to fail these assertions. All valid
     * and invalid graphs (those with version/module/capability conflicts, or resolution failures)
     * should pass the assertions in this method.
     */
    private static void assertHasValidGraphStructure(ResolveState resolveState) {
        if (!areAssertsEnabled()) {
            return;
        }

        for (ModuleResolveState module : resolveState.getModules()) {
            if (module.isInModuleConflict()) {
                throw new IllegalStateException(String.format("Module %s is in conflict", module));
            }
            for (EdgeState unattachedEdge : module.getUnattachedEdges()) {
                if (unattachedEdge.getFailure() == null) {
                    throw new IllegalStateException(String.format("Module %s has non-failing unattached edge: %s", module, unattachedEdge));
                }
                if (!unattachedEdge.isUnattached()) {
                    throw new IllegalStateException(String.format("Module %s has unattached edge that is not unattached: %s", module, unattachedEdge));
                }
            }
            for (ComponentState component : module.getVersions()) {
                for (NodeState node : component.getNodes()) {
                    if (node.isInCapabilityConflict()) {
                        throw new IllegalStateException(String.format("Node %s is in capability conflict", node));
                    }
                    for (EdgeState incomingEdge : node.getIncomingEdges()) {
                        NodeState from = incomingEdge.getFrom();
                        if (!from.getOutgoingEdges().contains(incomingEdge)) {
                            throw new IllegalStateException(String.format(
                                "Node %s has incoming edge from %s, but source node does not declare outgoing edge.",
                                node.getDisplayName(),
                                from.getDisplayName()
                            ));
                        }
                        if (!from.isSelected()) {
                            throw new IllegalStateException(String.format(
                                "Node %s has an incoming edge from %s, but source node is not part of the graph.",
                                from.getDisplayName(),
                                node.getDisplayName()
                            ));
                        }
                    }
                    for (EdgeState outgoingEdge : node.getOutgoingEdges()) {
                        ModuleResolveState targetModule = outgoingEdge.getSelector().getTargetModule();
                        if (outgoingEdge.isUnattached() && !targetModule.getUnattachedEdges().contains(outgoingEdge)) {
                            throw new IllegalStateException(String.format(
                                "Outgoing unattached edge %s has target module %s, but module does not declare edge as unattached",
                                outgoingEdge,
                                targetModule
                            ));
                        }
                        for (NodeState target : outgoingEdge.getTargetNodes()) {
                            if (!target.getIncomingEdges().contains(outgoingEdge)) {
                                throw new IllegalStateException(String.format(
                                    "Node %s has an outgoing edge to node %s, but target node does not declare incoming edge.",
                                    node.getDisplayName(),
                                    target.getDisplayName()
                                ));
                            }
                        }
                        boolean hasTargetNodes = !outgoingEdge.getTargetNodes().isEmpty();
                        boolean hasFailure = outgoingEdge.getFailure() != null;
                        if (hasTargetNodes == hasFailure) {
                            // Edges must either have a target node or a failure, but not both, and not neither.
                            throw new IllegalStateException(String.format(
                                "Node %s has an outgoing edge %s with inconsistent target nodes and failure.",
                                node.getDisplayName(),
                                outgoingEdge
                            ));
                        }
                    }
                }
            }
        }
    }

    public static boolean areAssertsEnabled() {
        boolean assertsEnabled = false;
        //noinspection AssertWithSideEffects
        assert assertsEnabled = true;
        return assertsEnabled;
    }

    private static boolean isDynamic(SelectorState selector) {
        ResolvedVersionConstraint versionConstraint = selector.getVersionConstraint();
        if (versionConstraint != null) {
            return versionConstraint.isDynamic();
        }
        return false;
    }

    private static void validateDynamicSelectors(ComponentState selected) {
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

    private static void checkIfDynamicVersionAllowed(ComponentState selected, List<SelectorState> selectors) {
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

    private static void markDeniedDynamicVersions(ComponentState cs) {
        for (NodeState node : cs.getNodes()) {
            List<EdgeState> incomingEdges = node.getIncomingEdges();
            for (EdgeState incomingEdge : incomingEdges) {
                ComponentSelector selector = incomingEdge.getSelector().getSelector();
                incomingEdge.failWith(new ModuleVersionResolveException(selector, () ->
                    String.format("Could not resolve %s: Resolution strategy disallows usage of dynamic versions", selector)));
            }
        }
    }

    private static void validateChangingVersions(ComponentState selected) {
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
     * Verify the given component was not selected via version conflict resolution.
     * In other words, ensure only one version of this component was requested.
     */
    private static void validateVersionConflicts(
        ComponentState selected,
        ResolutionParameters.FailureResolutions failureResolutions
    ) {
        if (!selected.getSelectionReason().isConflictResolution()) {
            return;
        }

        // This component was selected due to version conflict resolution.
        // Fail all incoming edges.

        Pair<Conflict, List<String>> resolutions = buildConflictResolutions(selected, failureResolutions);
        VersionConflictException failure = new VersionConflictException(resolutions.getLeft(), resolutions.getRight());

        for (NodeState node : selected.getNodes()) {
            for (EdgeState incomingEdge : node.getIncomingEdges()) {
                incomingEdge.failWith(failure);
            }
        }
    }

    private static Pair<Conflict, List<String>> buildConflictResolutions(ComponentState selected, ResolutionParameters.FailureResolutions failureResolutions) {
        ImmutableList<Conflict.Participant> participants = selected.getModule().getAllVersions().stream()
            .map(component -> new Conflict.Participant(component.getId().getVersion(), component.getComponentId()))
            .collect(ImmutableList.toImmutableList());

        Conflict conflict = new Conflict(
            participants,
            selected.getModuleVersion().getModule(),
            selected.getSelectionReason()
        );

        return new ImmutablePair<>(conflict, failureResolutions.forVersionConflict(conflict));
    }

    /**
     * Validates that all selected nodes of a single component have compatible attributes,
     * when using variant aware resolution.
     */
    private static void validateMultipleNodeSelection(
        ImmutableAttributesSchema consumerSchema,
        ModuleResolveState module,
        ComponentState selected,
        ResolutionFailureHandler resolutionFailureHandler,
        AttributeSchemaServices attributeSchemaServices
    ) {
        Set<NodeState> selectedNodes = selected.getNodes().stream()
            .filter(n -> n.isSelected() && !n.isAttachedToVirtualPlatform() && !n.hasShadowedCapability() && !n.isRejectedForCapabilityConflict())
            .collect(Collectors.toSet());

        if (selectedNodes.size() < 2) {
            return;
        }

        Set<Set<NodeState>> combinations = Sets.combinations(selectedNodes, 2);
        Set<NodeState> incompatibleNodes = new HashSet<>();

        AttributeMatcher matcher = attributeSchemaServices.getMatcher(consumerSchema, selected.getMetadata().getAttributesSchema());
        for (Set<NodeState> combination : combinations) {
            Iterator<NodeState> it = combination.iterator();
            NodeState first = it.next();
            NodeState second = it.next();

            if (!matcher.areMutuallyCompatible(first.getMetadata().getAttributes(), second.getMetadata().getAttributes())) {
                incompatibleNodes.add(first);
                incompatibleNodes.add(second);
            }
        }

        if (!incompatibleNodes.isEmpty()) {
            Set<VariantGraphResolveMetadata> incompatibleNodeMetadatas = incompatibleNodes.stream()
                .map(NodeState::getMetadata)
                .collect(Collectors.toSet());
            AbstractResolutionFailureException variantsSelectionException = resolutionFailureHandler.incompatibleMultipleNodesValidationFailure(matcher, selected.getMetadata(), incompatibleNodeMetadatas);
            module.visitIncomingEdges(edge -> edge.failWith(variantsSelectionException));
        }
    }

    private static void attachMultipleForceOnPlatformFailureToEdges(ModuleResolveState module) {
        List<EdgeState> forcedEdges = null;
        boolean hasMultipleVersions = false;
        String currentVersion = module.maybeFindForcedPlatformVersion();
        Set<ModuleResolveState> participatingModules = module.getPlatformState().getParticipatingModules();
        for (ModuleResolveState participatingModule : participatingModules) {
            ComponentState selected = participatingModule.getSelected();
            if (selected != null) {
                for (NodeState nodeState : selected.getNodes()) {
                    for (EdgeState incomingEdge : nodeState.getIncomingEdges()) {
                        SelectorState selector = incomingEdge.getSelector();
                        if (isPlatformForcedEdge(selector)) {
                            ComponentSelector componentSelector = selector.getSelector();
                            if (componentSelector instanceof ModuleComponentSelector) {
                                ModuleComponentSelector mcs = (ModuleComponentSelector) componentSelector;
                                if (!incomingEdge.getFrom().getComponent().getModule().equals(module)) {
                                    if (forcedEdges == null) {
                                        forcedEdges = new ArrayList<>();
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
            }
        }
        if (hasMultipleVersions) {
            GradleException failure = new GradleException("Multiple forces on different versions for virtual platform " + module.getId());
            forcedEdges.forEach(edge -> edge.failWith(failure));
        }
    }

    private static boolean isPlatformForcedEdge(SelectorState selector) {
        return selector.isForce() && !selector.isSoftForce();
    }

    /**
     * Populates the result from the graph traversal state.
     */
    private static void assembleResult(ResolveState resolveState, DependencyGraphVisitor visitor) {
        visitor.start(resolveState.getRoot());

        // Visit the nodes prior to visiting the edges
        for (NodeState nodeState : resolveState.getNodes()) {
            if (nodeState.shouldIncludedInGraphResult()) {
                visitor.visitNode(nodeState);
            }
        }

        // Collect the components to sort in consumer-first order
        LinkedList<ComponentState> queue = new LinkedList<>();
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
