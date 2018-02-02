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
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionApplicator;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusion;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.component.local.model.LocalConfigurationMetadata;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Represents a node in the dependency graph.
 */
class NodeState implements DependencyGraphNode {
    private static final Logger LOGGER = LoggerFactory.getLogger(DependencyGraphBuilder.class);

    private final Long resultId;
    private final ComponentState component;
    private final List<EdgeState> incomingEdges = Lists.newLinkedList();
    private final List<EdgeState> outgoingEdges = Lists.newLinkedList();
    private final boolean isTransitive;

    private final ResolvedConfigurationIdentifier id;

    private final ConfigurationMetadata metaData;
    private final ResolveState resolveState;
    private ModuleExclusion previousTraversalExclusions;
    private boolean hasTransitiveIncomingEdges;

    NodeState(Long resultId, ResolvedConfigurationIdentifier id, ComponentState component, ResolveState resolveState, ConfigurationMetadata md) {
        this.resultId = resultId;
        this.id = id;
        this.component = component;
        this.resolveState = resolveState;
        this.metaData = md;
        component.addConfiguration(this);
        // cached because there are more than 2 implementations of metadata, so the JIT is defeated
        this.isTransitive = metaData.isTransitive();
    }

    ComponentState getComponent() {
        return component;
    }

    @Override
    public Long getNodeId() {
        return resultId;
    }

    @Override
    public boolean isRoot() {
        return false;
    }

    @Override
    public ResolvedConfigurationIdentifier getResolvedConfigurationId() {
        return id;
    }

    @Override
    public ComponentState getOwner() {
        return component;
    }

    @Override
    public List<EdgeState> getIncomingEdges() {
        return incomingEdges;
    }

    @Override
    public List<EdgeState> getOutgoingEdges() {
        return outgoingEdges;
    }

    @Override
    public ConfigurationMetadata getMetadata() {
        return metaData;
    }

    @Override
    public Set<? extends LocalFileDependencyMetadata> getOutgoingFileEdges() {
        if (metaData instanceof LocalConfigurationMetadata) {
            // Only when this node has a transitive incoming edge
            for (EdgeState incomingEdge : incomingEdges) {
                if (incomingEdge.isTransitive()) {
                    return ((LocalConfigurationMetadata) metaData).getFiles();
                }
            }
        }
        return Collections.emptySet();
    }

    @Override
    public String toString() {
        return String.format("%s(%s)", component, id.getConfiguration());
    }

    public boolean isTransitive() {
        return isTransitive;
    }

    public void visitOutgoingDependencies(Collection<EdgeState> target, PendingDependenciesHandler pendingDependenciesHandler) {
        // If this configuration's version is in conflict, don't do anything
        // If not traversed before, add all selected outgoing edges
        // If traversed before, and the selected modules have changed, remove previous outgoing edges and add outgoing edges again with
        //    the new selections.
        // If traversed before, and the selected modules have not changed, ignore
        // If none of the incoming edges are transitive, then the node has no outgoing edges

        if (!component.isSelected()) {
            LOGGER.debug("version for {} is not selected. ignoring.", this);
            return;
        }

        boolean hasIncomingEdges = !incomingEdges.isEmpty();

        if (!hasTransitiveIncomingEdges && !isRoot()) {
            if (previousTraversalExclusions != null) {
                removeOutgoingEdges();
            }
            if (hasIncomingEdges) {
                LOGGER.debug("{} has no transitive incoming edges. ignoring outgoing edges.", this);
            } else {
                LOGGER.debug("{} has no incoming edges. ignoring.", this);
            }
            return;
        }

        ModuleExclusion resolutionFilter = getModuleResolutionFilter();
        if (previousTraversalExclusions != null) {
            if (previousTraversalExclusions.excludesSameModulesAs(resolutionFilter)) {
                LOGGER.debug("Changed edges for {} selects same versions as previous traversal. ignoring", this);
                // Don't need to traverse again, but hang on to the new filter as the set of artifacts may have changed
                previousTraversalExclusions = resolutionFilter;
                return;
            }
            removeOutgoingEdges();
        }

        visitDependencies(resolutionFilter, pendingDependenciesHandler, target);

    }

    protected void visitDependencies(ModuleExclusion resolutionFilter, PendingDependenciesHandler pendingDependenciesHandler, Collection<EdgeState> resultingOutgoingEdges) {
        PendingDependenciesHandler.Visitor pendingDepsVisitor =  pendingDependenciesHandler.start();
        try {
            for (DependencyMetadata dependency : metaData.getDependencies()) {
                DependencyState dependencyState = new DependencyState(dependency, resolveState.getComponentSelectorConverter());
                if (isExcluded(resolutionFilter, dependencyState)) {
                    continue;
                }
                dependencyState = maybeSubstitute(dependencyState);
                if (!pendingDepsVisitor.maybeAddAsPendingDependency(this, dependencyState)) {
                    EdgeState dependencyEdge = new EdgeState(this, dependencyState, resolutionFilter, resolveState);
                    outgoingEdges.add(dependencyEdge);
                    resultingOutgoingEdges.add(dependencyEdge);
                }
            }
            previousTraversalExclusions = resolutionFilter;
        } finally {
            // we must do this after `previousTraversalExclusions` has been written, or state won't be reset properly
            pendingDepsVisitor.complete();
        }
    }

    // TODO:DAZ This should be done as a decorator on ConfigurationMetadata.getDependencies() ???
    private DependencyState maybeSubstitute(DependencyState dependencyState) {
        DependencySubstitutionApplicator.SubstitutionResult substitutionResult = resolveState.getDependencySubstitutionApplicator().apply(dependencyState.getDependency());
        if (substitutionResult.hasFailure()) {
            dependencyState.failure = new ModuleVersionResolveException(dependencyState.getRequested(), substitutionResult.getFailure());
            return dependencyState;
        }

        DependencySubstitutionInternal details = substitutionResult.getResult();
        if (details != null && details.isUpdated()) {
            return dependencyState.withTarget(details.getTarget(), details.getSelectionDescription());
        }
        return dependencyState;
    }

    private boolean isExcluded(ModuleExclusion selector, DependencyState dependencyState) {
        DependencyMetadata dependency = dependencyState.getDependency();
        if (!resolveState.getEdgeFilter().isSatisfiedBy(dependency)) {
            LOGGER.debug("{} is filtered.", dependency);
            return true;
        }
        if (selector == ModuleExclusions.excludeNone()) {
            return false;
        }
        ModuleIdentifier targetModuleId = dependencyState.getModuleIdentifier();
        if (selector.excludeModule(targetModuleId)) {
            LOGGER.debug("{} is excluded from {}.", targetModuleId, this);
            return true;
        }

        return false;
    }

    public void addIncomingEdge(EdgeState dependencyEdge) {
        incomingEdges.add(dependencyEdge);
        hasTransitiveIncomingEdges |= dependencyEdge.isTransitive();
        resolveState.onMoreSelected(this);
    }

    public void removeIncomingEdge(EdgeState dependencyEdge) {
        incomingEdges.remove(dependencyEdge);
        hasTransitiveIncomingEdges = false;
        for (EdgeState incomingEdge : incomingEdges) {
            if (incomingEdge.isTransitive()) {
                hasTransitiveIncomingEdges = true;
                break;
            }
        }
        resolveState.onFewerSelected(this);
    }

    public boolean isSelected() {
        return !incomingEdges.isEmpty();
    }

    private ModuleExclusion getModuleResolutionFilter() {
        ModuleExclusions moduleExclusions = resolveState.getModuleExclusions();
        ModuleExclusion nodeExclusions = moduleExclusions.excludeAny(metaData.getExcludes());
        if (!hasTransitiveIncomingEdges) {
            return nodeExclusions;
        }
        ModuleExclusion edgeExclusions = null;
        for (EdgeState incomingEdge : incomingEdges) {
            if (incomingEdge.isTransitive()) {
                edgeExclusions = edgeExclusions == null ? incomingEdge.getExclusions() : moduleExclusions.union(edgeExclusions, incomingEdge.getExclusions());
            }
        }
        return moduleExclusions.intersect(edgeExclusions, nodeExclusions);
    }

    public void removeOutgoingEdges() {
        for (EdgeState outgoingDependency : outgoingEdges) {
            outgoingDependency.removeFromTargetConfigurations();
        }
        outgoingEdges.clear();
        previousTraversalExclusions = null;
    }

    public void restart(ComponentState selected) {
        // Restarting this configuration after conflict resolution.
        // If this configuration belongs to the select version, queue ourselves up for traversal.
        // If not, then remove our incoming edges, which triggers them to be moved across to the selected configuration
        if (component == selected) {
            resolveState.onMoreSelected(this);
        } else {
            if (!incomingEdges.isEmpty()) {
                restartIncomingEdges(selected);
            }
        }
    }

    private void restartIncomingEdges(ComponentState selected) {
        if (incomingEdges.size() == 1) {
            incomingEdges.iterator().next().restart(selected);
        } else {
            for (EdgeState dependency : new ArrayList<EdgeState>(incomingEdges)) {
                dependency.restart(selected);
            }
        }
        incomingEdges.clear();
        hasTransitiveIncomingEdges = false;
    }

    public void deselect() {
        removeOutgoingEdges();
    }

    void resetSelectionState() {
        previousTraversalExclusions = null;
        outgoingEdges.clear();
        resolveState.onMoreSelected(this);
    }

    public ImmutableAttributesFactory getAttributesFactory() {
        return resolveState.getAttributesFactory();
    }
}
