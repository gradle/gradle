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
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusion;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.internal.component.local.model.LocalConfigurationMetadata;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents a node in the dependency graph.
 */
class NodeState implements DependencyGraphNode {
    private static final Logger LOGGER = LoggerFactory.getLogger(DependencyGraphBuilder.class);

    private final Long resultId;
    private final ComponentState component;
    private final Set<EdgeState> incomingEdges = new LinkedHashSet<EdgeState>();
    private final List<EdgeState> outgoingEdges = Lists.newLinkedList();
    private final ResolvedConfigurationIdentifier id;

    private final ConfigurationMetadata metaData;
    private final ResolveState resolveState;
    private ModuleExclusion previousTraversalExclusions;

    NodeState(Long resultId, ResolvedConfigurationIdentifier id, ComponentState component, ResolveState resolveState) {
        this(resultId, id, component, resolveState, component.getMetadata().getConfiguration(id.getConfiguration()));
    }

    NodeState(Long resultId, ResolvedConfigurationIdentifier id, ComponentState component, ResolveState resolveState, ConfigurationMetadata md) {
        this.resultId = resultId;
        this.id = id;
        this.component = component;
        this.resolveState = resolveState;
        this.metaData = md;
        component.addConfiguration(this);
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
    public Set<EdgeState> getIncomingEdges() {
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
        return metaData.isTransitive();
    }

    public void visitOutgoingDependencies(Collection<EdgeState> target, OptionalDependenciesHandler optionalDependenciesHandler) {
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
        List<EdgeState> transitiveIncoming = findTransitiveIncomingEdges(hasIncomingEdges);

        if (transitiveIncoming.isEmpty() && !isRoot()) {
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

        ModuleExclusion resolutionFilter = getModuleResolutionFilter(transitiveIncoming);
        if (previousTraversalExclusions != null) {
            if (previousTraversalExclusions.excludesSameModulesAs(resolutionFilter)) {
                LOGGER.debug("Changed edges for {} selects same versions as previous traversal. ignoring", this);
                // Don't need to traverse again, but hang on to the new filter as the set of artifacts may have changed
                previousTraversalExclusions = resolutionFilter;
                return;
            }
            removeOutgoingEdges();
        }

        visitDependencies(resolutionFilter, optionalDependenciesHandler, target);

    }

    protected void visitDependencies(ModuleExclusion resolutionFilter, OptionalDependenciesHandler optionalDependenciesHandler, Collection<EdgeState> resultingOutgoingEdges) {
        boolean isOptionalConfiguration = "optional".equals(metaData.getName());
        OptionalDependenciesHandler.Visitor optionalDepsVisitor =  optionalDependenciesHandler.start(isOptionalConfiguration);
        try {
            for (DependencyMetadata dependency : metaData.getDependencies()) {
                DependencyState dependencyState = new DependencyState(dependency, resolveState.getComponentSelectorConverter());
                if (isExcluded(resolutionFilter, dependencyState)) {
                    continue;
                }
                if (!optionalDepsVisitor.maybeAddAsOptionalDependency(this, dependencyState)) {
                    EdgeState dependencyEdge = new EdgeState(this, dependencyState, resolutionFilter, resolveState);
                    outgoingEdges.add(dependencyEdge);
                    resultingOutgoingEdges.add(dependencyEdge);
                }
            }
            previousTraversalExclusions = resolutionFilter;
        } finally {
            // we must do this after `previousTraversalExclusions` has been written, or state won't be reset properly
            optionalDepsVisitor.complete();
        }
    }

    private List<EdgeState> findTransitiveIncomingEdges(boolean hasIncomingEdges) {
        if (!hasIncomingEdges) {
            return Collections.emptyList();
        }

        int size = incomingEdges.size();
        if (size == 1) {
            return findSingleIncomingEdge();
        }

        List<EdgeState> transitiveIncoming = Lists.newArrayListWithCapacity(size);
        for (EdgeState edge : incomingEdges) {
            if (edge.isTransitive()) {
                transitiveIncoming.add(edge);
            }
        }
        return transitiveIncoming;

    }

    private List<EdgeState> findSingleIncomingEdge() {
        EdgeState edgeState = incomingEdges.iterator().next();
        if (edgeState.isTransitive()) {
            return Collections.singletonList(edgeState);
        } else {
            return Collections.emptyList();
        }
    }

    private boolean isExcluded(ModuleExclusion selector, DependencyState dependencyState) {
        DependencyMetadata dependency = dependencyState.getDependencyMetadata();
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
        resolveState.onMoreSelected(this);
    }

    public void removeIncomingEdge(EdgeState dependencyEdge) {
        incomingEdges.remove(dependencyEdge);
        resolveState.onFewerSelected(this);
    }

    public boolean isSelected() {
        return !incomingEdges.isEmpty();
    }

    private ModuleExclusion getModuleResolutionFilter(List<EdgeState> transitiveEdges) {
        ModuleExclusion resolutionFilter;
        ModuleExclusions moduleExclusions = resolveState.getModuleExclusions();
        if (transitiveEdges.isEmpty()) {
            resolutionFilter = ModuleExclusions.excludeNone();
        } else {
            resolutionFilter = transitiveEdges.get(0).getExclusions(moduleExclusions);
            for (int i = 1; i < transitiveEdges.size(); i++) {
                EdgeState dependencyEdge = transitiveEdges.get(i);
                resolutionFilter = moduleExclusions.union(resolutionFilter, dependencyEdge.getExclusions(moduleExclusions));
            }
        }
        resolutionFilter = moduleExclusions.intersect(resolutionFilter, metaData.getExclusions(moduleExclusions));
        return resolutionFilter;
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
    }

    public void deselect() {
        removeOutgoingEdges();
    }

    void resetSelectionState() {
        previousTraversalExclusions = null;
        outgoingEdges.clear();
        resolveState.onMoreSelected(this);
    }
}
