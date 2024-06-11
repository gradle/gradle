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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.configurations.ConflictResolution;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.Version;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.CandidateModule;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.selectors.SelectorStateResolver;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeMergingException;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.component.model.ComponentGraphSpecificResolveState;
import org.gradle.internal.component.model.ComponentIdGenerator;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ForcingDependencyMetadata;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolution state for a given module.
 */
public class ModuleResolveState implements CandidateModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModuleResolveState.class);
    private static final int MAX_SELECTION_CHANGE = 1000;

    private final ComponentMetaDataResolver metaDataResolver;
    private final ComponentIdGenerator idGenerator;
    private final ModuleIdentifier id;
    private final List<EdgeState> unattachedDependencies = new LinkedList<>();
    private final Map<ModuleVersionIdentifier, ComponentState> versions = new LinkedHashMap<>();
    private final ModuleSelectors<SelectorState> selectors;
    private final ConflictResolution conflictResolution;
    private final ImmutableAttributesFactory attributesFactory;
    private final Comparator<Version> versionComparator;
    private final VersionParser versionParser;
    final ResolveOptimizations resolveOptimizations;
    private final boolean rootModule;
    private SelectorStateResolver<ComponentState> selectorStateResolver;
    private final PendingDependencies pendingDependencies;
    private ComponentState selected;
    private ImmutableAttributes mergedConstraintAttributes = ImmutableAttributes.EMPTY;

    private AttributeMergingException attributeMergingError;
    private VirtualPlatformState platformState;
    private boolean overriddenSelection;
    private Set<VirtualPlatformState> platformOwners;
    private boolean replaced = false;
    private boolean changingSelection;
    private int selectionChangedCounter;

    ModuleResolveState(
        ComponentIdGenerator idGenerator,
        ModuleIdentifier id,
        ComponentMetaDataResolver metaDataResolver,
        ImmutableAttributesFactory attributesFactory,
        Comparator<Version> versionComparator,
        VersionParser versionParser,
        SelectorStateResolver<ComponentState> selectorStateResolver,
        ResolveOptimizations resolveOptimizations,
        boolean rootModule,
        ConflictResolution conflictResolution
    ) {
        this.idGenerator = idGenerator;
        this.id = id;
        this.metaDataResolver = metaDataResolver;
        this.attributesFactory = attributesFactory;
        this.versionComparator = versionComparator;
        this.versionParser = versionParser;
        this.resolveOptimizations = resolveOptimizations;
        this.rootModule = rootModule;
        this.pendingDependencies = new PendingDependencies(id);
        this.selectorStateResolver = selectorStateResolver;
        this.selectors = new ModuleSelectors<>(versionComparator, versionParser);
        this.conflictResolution = conflictResolution;
    }

    void setSelectorStateResolver(SelectorStateResolver<ComponentState> selectorStateResolver) {
        this.selectorStateResolver = selectorStateResolver;
    }

    void registerPlatformOwner(VirtualPlatformState owner) {
        if (platformOwners == null) {
            platformOwners = Sets.newHashSetWithExpectedSize(1);
        }
        platformOwners.add(owner);
    }

    public Set<VirtualPlatformState> getPlatformOwners() {
        return platformOwners == null ? Collections.emptySet() : platformOwners;
    }

    @Override
    public String toString() {
        return id.toString();
    }

    @Override
    public ModuleIdentifier getId() {
        return id;
    }

    @Override
    public Collection<ComponentState> getVersions() {
        if (this.versions.isEmpty()) {
            return Collections.emptyList();
        }
        Collection<ComponentState> values = this.versions.values();
        if (areAllCandidatesForSelection(values)) {
            return values;
        }
        List<ComponentState> versions = Lists.newArrayListWithCapacity(values.size());
        for (ComponentState componentState : values) {
            if (componentState.isCandidateForConflictResolution()) {
                versions.add(componentState);
            }
        }
        return versions;
    }

    public Collection<ComponentState> getAllVersions() {
        return this.versions.values();
    }

    private static boolean areAllCandidatesForSelection(Collection<ComponentState> values) {
        boolean allCandidates = true;
        for (ComponentState value : values) {
            if (!value.isCandidateForConflictResolution()) {
                allCandidates = false;
                break;
            }
        }
        return allCandidates;
    }

    @Nullable
    public ComponentState getSelected() {
        return selected;
    }

    /**
     * Selects the target component for this module for the first time.
     * Any existing versions will be evicted.
     */
    public void select(ComponentState selected) {
        assert this.selected == null;
        this.selected = selected;
        this.replaced = false;

        selectComponentAndEvictOthers(selected);
    }

    private void selectComponentAndEvictOthers(ComponentState selected) {
        for (ComponentState version : versions.values()) {
            version.evict();
        }
        selected.select();
    }

    public boolean isChangingSelection() {
        return changingSelection;
    }

    /**
     * Changes the selected target component for this module.
     */
    private void changeSelection(ComponentState newSelection) {
        assert this.selected != null;
        assert newSelection != null;
        assert this.selected != newSelection;
        assert newSelection.getModule() == this;

        changingSelection = true;

        // Remove any outgoing edges for the current selection
        selected.removeOutgoingEdges();

        this.selected = newSelection;
        this.replaced = false;

        doRestart(newSelection);
        changingSelection = false;
    }

    /**
     * Clears the current selection for the module, to prepare for conflict resolution.
     * - For the current selection, disconnect and remove any outgoing dependencies.
     * - Make all 'selected' component versions selectable.
     */
    public void clearSelection() {
        if (selected != null) {
            selected.removeOutgoingEdges();
        }
        for (ComponentState version : versions.values()) {
            if (version.isSelected()) {
                version.makeSelectable();
            }
        }

        selected = null;
        replaced = false;
    }

    /**
     * Overrides the component selection for this module, when this module has been replaced by another.
     */
    @Override
    public void replaceWith(ComponentState selected) {
        if (this.selected != null) {
            clearSelection();
        }

        assert this.selected == null;
        assert selected != null;

        if (!selected.getId().getModule().equals(getId())) {
            this.overriddenSelection = true;
        }
        this.selected = selected;
        this.replaced = computeReplaced(selected);

        doRestart(selected);
    }

    private boolean computeReplaced(ComponentState selected) {
        // This module might be resolved to a different module, through replacedBy
        return !selected.getId().getModule().equals(getId());
    }

    private void doRestart(ComponentState selected) {
        selectComponentAndEvictOthers(selected);
        for (ComponentState version : versions.values()) {
            version.restartIncomingEdges(selected);
        }
        for (SelectorState selector : selectors) {
            selector.overrideSelection(selected);
        }
        if (!unattachedDependencies.isEmpty()) {
            restartUnattachedDependencies();
        }
    }

    private void restartUnattachedDependencies() {
        if (unattachedDependencies.size() == 1) {
            EdgeState singleDependency = unattachedDependencies.get(0);
            singleDependency.restart();
        } else {
            for (EdgeState dependency : new ArrayList<>(unattachedDependencies)) {
                dependency.restart();
            }
        }
    }

    public void addUnattachedDependency(EdgeState edge) {
        unattachedDependencies.add(edge);
        edge.markUnattached();
    }

    public void removeUnattachedDependency(EdgeState edge) {
        if (unattachedDependencies.remove(edge)) {
            edge.markAttached();
        }
    }

    public ComponentState getVersion(ModuleVersionIdentifier id, ComponentIdentifier componentIdentifier) {
        assert id.getModule().equals(this.id);
        return versions.computeIfAbsent(id, k ->
            new ComponentState(idGenerator.nextGraphNodeId(), this, id, componentIdentifier, metaDataResolver)
        );
    }

    void addSelector(SelectorState selector, boolean deferSelection) {
        selectors.add(selector, deferSelection);
        mergedConstraintAttributes = appendAttributes(mergedConstraintAttributes, selector);
        if (overriddenSelection) {
            assert selected != null : "An overridden module cannot have selected == null";
            selector.overrideSelection(selected);
        }
    }

    void removeSelector(SelectorState selector, ResolutionConflictTracker conflictTracker) {
        selectors.remove(selector);
        boolean alreadyReused = selector.markForReuse();
        mergedConstraintAttributes = ImmutableAttributes.EMPTY;
        for (SelectorState selectorState : selectors) {
            mergedConstraintAttributes = appendAttributes(mergedConstraintAttributes, selectorState);
        }
        if (!alreadyReused && selectors.size() != 0 && selected != null) {
            maybeUpdateSelection(conflictTracker);
        }
    }

    public ModuleSelectors<SelectorState> getSelectors() {
        return selectors;
    }

    List<EdgeState> getUnattachedDependencies() {
        return unattachedDependencies;
    }

    ImmutableAttributes mergedConstraintsAttributes(AttributeContainer append) throws AttributeMergingException {
        if (attributeMergingError != null) {
            throw new IllegalStateException(IncompatibleDependencyAttributesMessageBuilder.buildMergeErrorMessage(this, attributeMergingError));
        }
        ImmutableAttributes attributes = ((AttributeContainerInternal) append).asImmutable();
        if (mergedConstraintAttributes.isEmpty()) {
            return attributes;
        }
        return attributesFactory.safeConcat(mergedConstraintAttributes.asImmutable(), attributes);
    }

    private ImmutableAttributes appendAttributes(ImmutableAttributes dependencyAttributes, SelectorState selectorState) {
        try {
            DependencyMetadata dependencyMetadata = selectorState.getDependencyMetadata();
            boolean constraint = dependencyMetadata.isConstraint();
            if (constraint) {
                ComponentSelector selector = dependencyMetadata.getSelector();
                ImmutableAttributes attributes = ((AttributeContainerInternal) selector.getAttributes()).asImmutable();
                dependencyAttributes = attributesFactory.safeConcat(attributes, dependencyAttributes);
            }
        } catch (AttributeMergingException e) {
            attributeMergingError = e;
        }
        return dependencyAttributes;
    }

    Set<EdgeState> getIncomingEdges() {
        Set<EdgeState> incoming = new LinkedHashSet<>();
        if (selected != null) {
            for (NodeState nodeState : selected.getNodes()) {
                incoming.addAll(nodeState.getIncomingEdges());
            }
        }
        return incoming;
    }

    VirtualPlatformState getPlatformState() {
        if (platformState == null) {
            platformState = new VirtualPlatformState(versionComparator, versionParser, this, resolveOptimizations);
        }
        return platformState;
    }

    boolean isVirtualPlatform() {
        return platformState != null && !platformState.getParticipatingModules().isEmpty();
    }

    void decreaseHardEdgeCount(NodeState removalSource) {
        pendingDependencies.decreaseHardEdgeCount();
        if (pendingDependencies.isPending()) {
            // Back to being a pending dependency
            // Clear remaining incoming edges, as they must be all from constraints
            if (selected != null) {
                for (NodeState node : selected.getNodes()) {
                    node.clearConstraintEdges(pendingDependencies, removalSource);
                }
            }
        }
    }

    boolean isPending() {
        return pendingDependencies.isPending();
    }

    PendingDependencies getPendingDependencies() {
        return pendingDependencies;
    }

    void registerConstraintProvider(NodeState node) {
        pendingDependencies.registerConstraintProvider(node);
    }

    void unregisterConstraintProvider(NodeState nodeState) {
        pendingDependencies.unregisterConstraintProvider(nodeState);
    }

    public void maybeUpdateSelection(ResolutionConflictTracker conflictTracker) {
        if (replaced) {
            // Never update selection for a replaced module
            return;
        }
        if (!rootModule && selectors.checkDeferSelection()) {
            // Selection deferred as we know another selector will be added soon
            return;
        }
        ComponentState newSelected = selectorStateResolver.selectBest(getId(), selectors);
        newSelected.setSelectors(selectors);
        if (selected == null) {
            // In some cases we should ignore this because the selection happens to be a known conflict
            if (!conflictTracker.hasKnownConflict(newSelected.getId())) {
                select(newSelected);
            }
        } else if (newSelected != selected) {
            if (++selectionChangedCounter > MAX_SELECTION_CHANGE) {
                // Let's ignore modules that are changing selection way too much, by keeping the highest version
                if (maybeSkipSelectionChange(newSelected)) {
                    return;
                }
            }
            changeSelection(newSelected);
        }
    }

    private boolean maybeSkipSelectionChange(ComponentState newSelected) {
        if (selectionChangedCounter == MAX_SELECTION_CHANGE + 1) {
            LOGGER.warn("The dependency resolution engine wasn't able to find a version of module {} which satisfied all requirements because the graph wasn't stable enough. " +
                "The highest version was selected in order to stabilize selection.\n" +
                "Features available in a stable graph like version alignment are not guaranteed in this case.", id);
        }
        boolean newSelectedIsProject = false;
        if (conflictResolution == ConflictResolution.preferProjectModules) {
            if (newSelected.getComponentId() instanceof ProjectComponentIdentifier) {
                // Keep the project selected
                newSelectedIsProject = true;
            }
        }
        Version newVersion = versionParser.transform(newSelected.getVersion());
        Version currentVersion = versionParser.transform(selected.getVersion());
        return !newSelectedIsProject && versionComparator.compare(newVersion, currentVersion) <= 0;
    }

    void maybeCreateVirtualMetadata(ResolveState resolveState) {
        for (ComponentState componentState : versions.values()) {
            if (componentState.getMetadataOrNull() == null) {
                // TODO LJA Using the root as the NodeState here is a bit of a cheat, investigate if we can track the proper NodeState
                componentState.setState(LenientPlatformGraphResolveState.of(idGenerator, (ModuleComponentIdentifier) componentState.getComponentId(), componentState.getId(), platformState, resolveState.getRoot(), resolveState), ComponentGraphSpecificResolveState.EMPTY_STATE);
            }
        }
    }

    @Nullable
    String maybeFindForcedPlatformVersion() {
        ComponentState selected = getSelected();
        for (NodeState node : selected.getNodes()) {
            if (node.isSelected()) {
                for (EdgeState incomingEdge : node.getIncomingEdges()) {
                    DependencyMetadata dependencyMetadata = incomingEdge.getDependencyMetadata();
                    if (!(dependencyMetadata instanceof LenientPlatformDependencyMetadata) && dependencyMetadata instanceof ForcingDependencyMetadata) {
                        if (((ForcingDependencyMetadata) dependencyMetadata).isForce()) {
                            return selected.getVersion();
                        }
                    }
                }
            }
        }

        return null;
    }
}
