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
import com.google.common.collect.Lists;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusion;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.local.model.DslOriginDependencyMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.util.CollectionUtils;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * Represents the edges in the dependency graph.
 *
 * A dependency can have the following states:
 * 1. Unattached: in this case the state of the dependency is  tied to the state of it's associated {@link SelectorState}.
 * 2. Attached: in this case the Edge has been connected to actual nodes in the target component. Only possible if the {@link SelectorState} did not fail to resolve.
 */
class EdgeState implements DependencyGraphEdge {
    private final DependencyState dependencyState;
    private final DependencyMetadata dependencyMetadata;
    private final NodeState from;
    private final SelectorState selector;
    private final ResolveState resolveState;
    private final ModuleExclusion transitiveExclusions;
    private final List<NodeState> targetNodes = Lists.newLinkedList();
    private final boolean isTransitive;

    private ModuleVersionResolveException targetNodeSelectionFailure;

    EdgeState(NodeState from, DependencyState dependencyState, ModuleExclusion transitiveExclusions, ResolveState resolveState) {
        this.from = from;
        this.dependencyState = dependencyState;
        this.dependencyMetadata = dependencyState.getDependency();
        // The accumulated exclusions that apply to this edge based on the path from the root
        this.transitiveExclusions = transitiveExclusions;
        this.resolveState = resolveState;
        this.selector = resolveState.getSelector(dependencyState, dependencyState.getModuleIdentifier());
        this.isTransitive = from.isTransitive() && dependencyMetadata.isTransitive();
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

    /**
     * Returns the target component, if the edge has been successfully resolved.
     * Returns null if the edge failed to resolve, or has not (yet) been successfully resolved to a target component.
     */
    @Nullable
    ComponentState getTargetComponent() {
        if (!selector.isResolved() || selector.getFailure() != null) {
            return null;
        }
        return getSelectedComponent();
    }

    @Override
    public SelectorState getSelector() {
        return selector;
    }

    public boolean isTransitive() {
        return isTransitive;
    }

    public void attachToTargetConfigurations() {
        ComponentState targetComponent = getTargetComponent();
        if (targetComponent == null) {
            // The selector failed or the module has been deselected. Do not attach.
            return;
        }
        calculateTargetConfigurations(targetComponent);
        for (NodeState targetConfiguration : targetNodes) {
            targetConfiguration.addIncomingEdge(this);
        }
        if (!targetNodes.isEmpty()) {
            selector.getTargetModule().removeUnattachedDependency(this);
        }
        for (EdgeState state : from.getOutgoingEdges()) {
            state.tryAlignmentTo(targetComponent);
        }
    }

    public void removeFromTargetConfigurations() {
        if (!targetNodes.isEmpty()) {
            for (NodeState targetConfiguration : targetNodes) {
                targetConfiguration.removeIncomingEdge(this);
            }
            targetNodes.clear();
        }
        targetNodeSelectionFailure = null;
    }

    /**
     * Call this method to attach a failure late in the process. This is typically
     * done when a failure is caused by graph validation. In that case we want to
     * perform as much resolution as possible, still have a valid graph, but in the
     * end fail resolution.
     */
    public void failWith(Throwable err) {
        targetNodeSelectionFailure = new ModuleVersionResolveException(dependencyState.getRequested(), err);
    }

    public void restart() {
        if (from.isSelected()) {
            removeFromTargetConfigurations();
            attachToTargetConfigurations();
        }
    }

    private void tryAlignmentTo(ComponentState selectedTarget) {
        ComponentState fromComponent = from.getComponent();
        ModuleVersionIdentifier fromId = fromComponent.getId();
        ModuleVersionIdentifier targetId = selectedTarget.getId();
        if (shouldTryToAlign(fromId, targetId) && !fromComponent.getModule().hasSeenVersion(targetId.getVersion())) {
            ModuleIdentifier fromModule = fromId.getModule();
            final ModuleComponentSelector cs = DefaultModuleComponentSelector.newSelector(fromModule, targetId.getVersion());
            DependencyState dependencyState = new DependencyState(new AlignmentDependencyMetadata(cs), resolveState.getComponentSelectorConverter());
            for (NodeState nodeState : selectedTarget.getNodes()) {
                if (nodeState.isSelected()) {
                    maybeAddAlignmentEdge(fromComponent, targetId, fromModule, dependencyState, nodeState);
                }
            }
        }
    }

    private void maybeAddAlignmentEdge(ComponentState fromComponent, ModuleVersionIdentifier targetId, ModuleIdentifier fromModule, DependencyState dependencyState, NodeState nodeState) {
        AlignmentEdgeState alignmentEdge = new AlignmentEdgeState(nodeState, dependencyState);
        ModuleVersionIdentifier alignVersionId = DefaultModuleVersionIdentifier.newId(fromModule, targetId.getVersion());
        ComponentState version = fromComponent.getModule().getVersion(alignVersionId, DefaultModuleComponentIdentifier.newId(alignVersionId));
        SelectorState selector = alignmentEdge.getSelector();
        version.selectedBy(selector);
        // We need to check if the target version exists. For this,
        // we have to try to get metadata for the aligned version. If it's there,
        // it means we can align, otherwise, we must NOT add the edge, or resolution
        // would fail
        ComponentResolveMetadata metadata = version.getMetadata();
        if (metadata != null) {
            nodeState.resetSelectionState();
            nodeState.addAlignmentEdge(alignmentEdge);
            selector.use();
        }
    }

    /**
     * Tells if we should try to align two modules. In practice this is a very weak test, and it
     * should be replaced with a better logic.
     */
    private static boolean shouldTryToAlign(ModuleVersionIdentifier fromId, ModuleVersionIdentifier targetId) {
        return fromId.getGroup().equals(targetId.getGroup()) && !fromId.getVersion().equals(targetId.getVersion());
    }

    public ImmutableAttributes getAttributes() {
        ModuleResolveState module = selector.getTargetModule();
        return module.getMergedSelectorAttributes();
    }

    private void calculateTargetConfigurations(ComponentState targetComponent) {
        targetNodes.clear();
        targetNodeSelectionFailure = null;
        ComponentResolveMetadata targetModuleVersion = targetComponent.getMetadata();
        if (targetModuleVersion == null) {
            // Broken version
            return;
        }

        List<ConfigurationMetadata> targetConfigurations;
        try {
            ImmutableAttributes attributes = resolveState.getRoot().getMetadata().getAttributes();
            attributes = resolveState.getAttributesFactory().concat(attributes, getAttributes());
            targetConfigurations = dependencyMetadata.selectConfigurations(attributes, targetModuleVersion, resolveState.getAttributesSchema());
        } catch (Throwable t) {
            // Failure to select the target variant/configurations from this component, given the dependency attributes/metadata.
            targetNodeSelectionFailure = new ModuleVersionResolveException(dependencyState.getRequested(), t);
            return;
        }
        for (ConfigurationMetadata targetConfiguration : targetConfigurations) {
            NodeState targetNodeState = resolveState.getNode(targetComponent, targetConfiguration);
            this.targetNodes.add(targetNodeState);
        }
    }

    @Override
    public ModuleExclusion getExclusions() {
        List<ExcludeMetadata> excludes = dependencyMetadata.getExcludes();
        if (excludes.isEmpty()) {
            return transitiveExclusions;
        }
        ModuleExclusion edgeExclusions = resolveState.getModuleExclusions().excludeAny(ImmutableList.copyOf(excludes));
        return resolveState.getModuleExclusions().intersect(edgeExclusions, transitiveExclusions);
    }

    @Override
    public boolean contributesArtifacts() {
        return !dependencyMetadata.isPending();
    }

    @Override
    public ComponentSelector getRequested() {
        return AttributeDesugaring.desugarSelector(dependencyState.getRequested(), from.getAttributesFactory());
    }

    @Override
    public ModuleVersionResolveException getFailure() {
        if (targetNodeSelectionFailure != null) {
            return targetNodeSelectionFailure;
        }
        ModuleVersionResolveException selectorFailure = selector.getFailure();
        if (selectorFailure != null) {
            return selectorFailure;
        }
        return getSelectedComponent().getMetadataResolveFailure();
    }

    @Override
    public Long getSelected() {
        return getSelectedComponent().getResultId();
    }

    @Override
    public ComponentSelectionReason getReason() {
        return selector.getSelectionReason();
    }

    private ComponentState getSelectedComponent() {
        return selector.getTargetModule().getSelected();
    }

    @Override
    public Dependency getOriginalDependency() {
        if (dependencyMetadata instanceof DslOriginDependencyMetadata) {
            return ((DslOriginDependencyMetadata) dependencyMetadata).getSource();
        }
        return null;
    }

    @Override
    public List<ComponentArtifactMetadata> getArtifacts(final ConfigurationMetadata targetConfiguration) {
        return CollectionUtils.collect(dependencyMetadata.getArtifacts(), new Transformer<ComponentArtifactMetadata, IvyArtifactName>() {
            @Override
            public ComponentArtifactMetadata transform(IvyArtifactName ivyArtifactName) {
                return targetConfiguration.artifact(ivyArtifactName);
            }
        });
    }

    private static class AlignmentDependencyMetadata implements ModuleDependencyMetadata {
        private final ModuleComponentSelector cs;

        AlignmentDependencyMetadata(ModuleComponentSelector cs) {
            this.cs = cs;
        }

        @Override
        public ModuleComponentSelector getSelector() {
            return cs;
        }

        @Override
        public ModuleDependencyMetadata withRequestedVersion(VersionConstraint requestedVersion) {
            return this;
        }

        @Override
        public ModuleDependencyMetadata withReason(String reason) {
            return this;
        }

        @Override
        public List<ConfigurationMetadata> selectConfigurations(ImmutableAttributes consumerAttributes, ComponentResolveMetadata targetComponent, AttributesSchemaInternal consumerSchema) {
            return Collections.emptyList();
        }

        @Override
        public List<ExcludeMetadata> getExcludes() {
            return Collections.emptyList();
        }

        @Override
        public List<IvyArtifactName> getArtifacts() {
            return Collections.emptyList();
        }

        @Override
        public DependencyMetadata withTarget(ComponentSelector target) {
            return this;
        }

        @Override
        public boolean isChanging() {
            return false;
        }

        @Override
        public boolean isTransitive() {
            return false;
        }

        @Override
        public boolean isPending() {
            return false;
        }

        @Override
        public String getReason() {
            return null;
        }
    }

    public class AlignmentEdgeState extends EdgeState {
        public AlignmentEdgeState(NodeState from, DependencyState dependencyState) {
            super(from, dependencyState, EdgeState.this.transitiveExclusions, EdgeState.this.resolveState);
        }

        @Override
        public String toString() {
            return "align to " + getDependencyMetadata().getSelector();
        }
    }

}
