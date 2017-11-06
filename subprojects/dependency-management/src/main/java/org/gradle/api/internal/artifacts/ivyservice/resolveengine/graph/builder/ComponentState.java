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

import com.google.common.collect.Sets;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ComponentResult;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphComponent;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.DefaultComponentOverrideMetadata;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.result.ComponentIdResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableComponentResolveResult;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Resolution state for a given component
 */
public class ComponentState implements ComponentResolutionState, ComponentResult, DependencyGraphComponent {
    private final ModuleVersionIdentifier id;
    private final ComponentMetaDataResolver resolver;
    private final Set<NodeState> nodes = new LinkedHashSet<NodeState>();
    private final Long resultId;
    private final ModuleResolveState module;
    private volatile ComponentResolveMetadata metaData;

    private ModuleState state = ModuleState.Selectable;
    private ComponentSelectionReason selectionReason = VersionSelectionReasons.REQUESTED;
    private ModuleVersionResolveException failure;
    private SelectorState selectedBy;
    private DependencyGraphBuilder.VisitState visitState = DependencyGraphBuilder.VisitState.NotSeen;
    Set<SelectorState> allResolvers;

    ComponentState(Long resultId, ModuleResolveState module, ModuleVersionIdentifier id, ComponentMetaDataResolver resolver) {
        this.resultId = resultId;
        this.module = module;
        this.id = id;
        this.resolver = resolver;
    }

    @Override
    public String toString() {
        return id.toString();
    }

    @Override
    public String getVersion() {
        return id.getVersion();
    }

    @Override
    public Long getResultId() {
        return resultId;
    }

    @Override
    public ModuleVersionIdentifier getId() {
        return id;
    }

    @Override
    public ModuleVersionIdentifier getModuleVersion() {
        return id;
    }

    public ModuleVersionResolveException getFailure() {
        return failure;
    }

    public DependencyGraphBuilder.VisitState getVisitState() {
        return visitState;
    }

    public void setVisitState(DependencyGraphBuilder.VisitState visitState) {
        this.visitState = visitState;
    }

    public Set<NodeState> getNodes() {
        return nodes;
    }

    ModuleResolveState getModule() {
        return module;
    }

    @Override
    public ComponentResolveMetadata getMetadata() {
        return metaData;
    }

    public void restart(ComponentState selected) {
        for (NodeState configuration : nodes) {
            configuration.restart(selected);
        }
    }

    public void selectedBy(SelectorState resolver) {
        if (selectedBy == null) {
            selectedBy = resolver;
            allResolvers = Sets.newLinkedHashSet();
        }
        allResolvers.add(resolver);
    }

    /**
     * Returns true if this module version can be resolved quickly (already resolved or local)
     *
     * @return true if it has been resolved in a cheap way
     */
    public boolean fastResolve() {
        if (metaData != null || failure != null) {
            return true;
        }

        ComponentIdResolveResult idResolveResult = selectedBy.getResolveResult();
        if (idResolveResult.getFailure() != null) {
            failure = idResolveResult.getFailure();
            return true;
        }
        if (idResolveResult.getMetaData() != null) {
            metaData = idResolveResult.getMetaData();
            return true;
        }
        return false;
    }

    public void resolve() {
        if (fastResolve()) {
            return;
        }

        ComponentIdResolveResult idResolveResult = selectedBy.getResolveResult();

        DefaultBuildableComponentResolveResult result = new DefaultBuildableComponentResolveResult();
        resolver.resolve(idResolveResult.getId(), DefaultComponentOverrideMetadata.forDependency(selectedBy.getDependencyMetadata()), result);
        if (result.getFailure() != null) {
            failure = result.getFailure();
            return;
        }
        metaData = result.getMetaData();
    }

    @Override
    public ComponentResolveMetadata getMetaData() {
        if (metaData == null) {
            resolve();
        }
        return metaData;
    }

    public ResolvedVersionConstraint getVersionConstraint() {
        return selectedBy == null ? null : selectedBy.getVersionConstraint();
    }

    @Override
    public boolean isResolved() {
        return metaData != null;
    }

    public void setMetaData(ComponentResolveMetadata metaData) {
        this.metaData = metaData;
        this.failure = null;
    }

    public void addConfiguration(NodeState node) {
        nodes.add(node);
    }

    @Override
    public ComponentSelectionReason getSelectionReason() {
        return selectionReason;
    }

    @Override
    public void setSelectionReason(ComponentSelectionReason reason) {
        this.selectionReason = reason;
    }

    @Override
    public ComponentIdentifier getComponentId() {
        return getMetaData().getComponentId();
    }

    @Override
    public Set<ComponentState> getDependents() {
        Set<ComponentState> incoming = new LinkedHashSet<ComponentState>();
        for (NodeState configuration : nodes) {
            for (EdgeState dependencyEdge : configuration.getIncomingEdges()) {
                incoming.add(dependencyEdge.getFrom().getComponent());
            }
        }
        return incoming;
    }

    boolean isSelected() {
        return state == ModuleState.Selected;
    }

    boolean isSelectable() {
        return state.isSelectable();
    }

    boolean isCandidateForConflictResolution() {
        return state.isCandidateForConflictResolution();
    }

    void evict() {
        state = ModuleState.Evicted;
    }

    void select() {
        state = ModuleState.Selected;
    }

    void makeSelectable() {
        state = ModuleState.Selectable;
    }
}
