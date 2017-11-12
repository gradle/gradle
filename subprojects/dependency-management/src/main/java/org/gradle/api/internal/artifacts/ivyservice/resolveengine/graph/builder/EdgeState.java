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
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusion;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphSelector;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.local.model.DslOriginDependencyMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.resolve.ModuleVersionResolveException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents the edges in the dependency graph.
 */
class EdgeState implements DependencyGraphEdge {
    private final DependencyMetadata dependencyMetadata;
    private final NodeState from;
    private final SelectorState selector;
    private final ResolveState resolveState;
    private final ModuleExclusion moduleExclusion;
    private final Set<NodeState> targetNodes = new LinkedHashSet<NodeState>();

    private ComponentState targetModuleRevision;
    private ModuleVersionResolveException targetNodeSelectionFailure;

    EdgeState(NodeState from, DependencyState dependencyState, ModuleExclusion moduleExclusion, ResolveState resolveState) {
        this.from = from;
        this.dependencyMetadata = dependencyState.getDependencyMetadata();
        this.moduleExclusion = moduleExclusion;
        this.resolveState = resolveState;
        this.selector = resolveState.getSelector(dependencyMetadata, dependencyState.getModuleIdentifier());
    }

    @Override
    public String toString() {
        return String.format("%s -> %s", from.toString(), dependencyMetadata);
    }

    @Override
    public NodeState getFrom() {
        return from;
    }

    DependencyMetadata getDependencyMetadata() {
        return dependencyMetadata;
    }

    ComponentState getTargetComponent() {
        return targetModuleRevision;
    }

    @Override
    public DependencyGraphSelector getSelector() {
        return selector;
    }

    /**
     * @return The resolved module version
     */
    public ComponentState resolveModuleRevisionId() {
        if (targetModuleRevision == null) {
            targetModuleRevision = selector.resolveModuleRevisionId();
            selector.getSelectedModule().addUnattachedDependency(this);
        }
        return targetModuleRevision;
    }

    public boolean isTransitive() {
        return from.isTransitive() && dependencyMetadata.isTransitive();
    }

    public void attachToTargetConfigurations() {
        if (!targetModuleRevision.isSelected()) {
            return;
        }
        calculateTargetConfigurations();
        for (NodeState targetConfiguration : targetNodes) {
            targetConfiguration.addIncomingEdge(this);
        }
        if (!targetNodes.isEmpty()) {
            selector.getSelectedModule().removeUnattachedDependency(this);
        }
    }

    public void removeFromTargetConfigurations() {
        for (NodeState targetConfiguration : targetNodes) {
            targetConfiguration.removeIncomingEdge(this);
        }
        targetNodes.clear();
        targetNodeSelectionFailure = null;
        if (targetModuleRevision != null) {
            selector.getSelectedModule().removeUnattachedDependency(this);
        }
    }

    public void restart(ComponentState selected) {
        removeFromTargetConfigurations();
        targetModuleRevision = selected;
        attachToTargetConfigurations();
    }

    private void calculateTargetConfigurations() {
        targetNodes.clear();
        targetNodeSelectionFailure = null;
        ComponentResolveMetadata targetModuleVersion = targetModuleRevision.getMetaData();
        if (targetModuleVersion == null) {
            // Broken version
            return;
        }

        ImmutableAttributes attributes = resolveState.getRoot().getMetadata().getAttributes();
        Set<ConfigurationMetadata> targetConfigurations;
        try {
            targetConfigurations = dependencyMetadata.selectConfigurations(attributes, from.getComponent().getMetadata(), from.getMetadata(), targetModuleVersion, resolveState.getAttributesSchema());
        } catch (Throwable t) {
//                 Broken selector
            targetNodeSelectionFailure = new ModuleVersionResolveException(dependencyMetadata.getSelector(), t);
            return;
        }
        for (ConfigurationMetadata targetConfiguration : targetConfigurations) {
            NodeState targetNodeState = resolveState.getNode(targetModuleRevision, targetConfiguration);
            this.targetNodes.add(targetNodeState);
        }
    }

    public ModuleExclusion toExclusions(DependencyMetadata md, ConfigurationMetadata from) {
        List<Exclude> excludes = md.getExcludes(from.getHierarchy());
        if (excludes.isEmpty()) {
            return ModuleExclusions.excludeNone();
        }
        return resolveState.getModuleExclusions().excludeAny(ImmutableList.copyOf(excludes));
    }

    @Override
    public ModuleExclusion getExclusions(ModuleExclusions moduleExclusions) {
        ModuleExclusion edgeExclusions = toExclusions(dependencyMetadata, from.getMetadata());
        return resolveState.getModuleExclusions().intersect(edgeExclusions, moduleExclusion);
    }

    @Override
    public ComponentSelector getRequested() {
        return dependencyMetadata.getSelector();
    }

    @Override
    public ModuleVersionResolveException getFailure() {
        if (targetNodeSelectionFailure != null) {
            return targetNodeSelectionFailure;
        }
        return selector.getFailure();
    }

    @Override
    public Long getSelected() {
        return selector.getSelected().getResultId();
    }

    @Override
    public ComponentSelectionReason getReason() {
        return selector.getSelectionReason();
    }

    @Override
    public ModuleDependency getModuleDependency() {
        if (dependencyMetadata instanceof DslOriginDependencyMetadata) {
            return ((DslOriginDependencyMetadata) dependencyMetadata).getSource();
        }
        return null;
    }

    @Override
    public Iterable<? extends DependencyGraphNode> getTargets() {
        return targetNodes;
    }

    @Override
    public Set<ComponentArtifactMetadata> getArtifacts(ConfigurationMetadata metaData1) {
        return dependencyMetadata.getArtifacts(from.getMetadata(), metaData1);
    }
}
