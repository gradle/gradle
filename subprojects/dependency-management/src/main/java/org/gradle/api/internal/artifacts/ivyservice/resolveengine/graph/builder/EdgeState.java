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

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.dsl.CapabilityHandler;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.internal.artifacts.dsl.dependencies.CapabilitiesHandlerInternal;
import org.gradle.api.internal.artifacts.dsl.dependencies.CapabilityInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusion;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphSelector;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.model.CapabilityDescriptor;
import org.gradle.internal.component.local.model.DslOriginDependencyMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.util.CollectionUtils;

import java.util.List;
import java.util.Set;

/**
 * Represents the edges in the dependency graph.
 */
class EdgeState implements DependencyGraphEdge {
    private final DependencyState dependencyState;
    private final DependencyMetadata dependencyMetadata;
    private final NodeState from;
    private final SelectorState selector;
    private final ResolveState resolveState;
    private final ModuleExclusion transitiveExclusions;
    private final List<NodeState> targetNodes = Lists.newLinkedList();

    private ComponentState targetModuleRevision;
    private ModuleVersionResolveException targetNodeSelectionFailure;

    EdgeState(NodeState from, DependencyState dependencyState, ModuleExclusion transitiveExclusions, ResolveState resolveState) {
        this.from = from;
        this.dependencyState = dependencyState;
        this.dependencyMetadata = dependencyState.getDependency();
        // The accumulated exclusions that apply to this edge based on the path from the root
        this.transitiveExclusions = transitiveExclusions;
        this.resolveState = resolveState;
        this.selector = resolveState.getSelector(dependencyState, dependencyState.getModuleIdentifier());
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
        List<ConfigurationMetadata> targetConfigurations;
        try {
            targetConfigurations = dependencyMetadata.selectConfigurations(attributes, targetModuleVersion, resolveState.getAttributesSchema());
        } catch (Throwable t) {
//                 Broken selector
            targetNodeSelectionFailure = new ModuleVersionResolveException(dependencyState.getRequested(), t);
            return;
        }
        for (ConfigurationMetadata targetConfiguration : targetConfigurations) {
            NodeState targetNodeState = resolveState.getNode(targetModuleRevision, targetConfiguration);
            this.targetNodes.add(targetNodeState);
            discoverAndApplyCapabilities(targetConfiguration);
        }
    }

    /**
     * This method is responsible for adding "discovered" capabilities to the graph capabilities
     * handler. It is possible, as we grow the graph, that new capabilities are defined, or that
     * a preference is finally expressed for modules already seen in the graph. If it is the case,
     * selection for affected modules is cleared, and re-executed.
     *
     * @param targetConfiguration the selected configuration
     */
    private void discoverAndApplyCapabilities(ConfigurationMetadata targetConfiguration) {
        ImmutableList<? extends CapabilityDescriptor> capabilities = targetConfiguration.getCapabilities();
        CapabilitiesHandlerInternal capabilitiesHandler = resolveState.getCapabilitiesHandler();
        for (final CapabilityDescriptor capability : capabilities) {
            final String prefer = capability.getPrefer();
            capabilitiesHandler.capability(capability.getName(), new Action<CapabilityHandler>() {
                @Override
                public void execute(CapabilityHandler handler) {
                    CapabilityInternal affected = (CapabilityInternal) handler;
                    ModuleIdentifier oldPreferred = affected.getPrefer();
                    Set<ModuleIdentifier> oldProvided = ImmutableSet.copyOf(affected.getProvidedBy());
                    for (String provider : capability.getProvidedBy()) {
                        handler.providedBy(provider);
                    }
                    if (prefer != null) {
                        CapabilityHandler.Preference preference = handler.prefer(prefer);
                        if (capability.getReason() != null) {
                            preference.because(capability.getReason());
                        }
                    }
                    if (!Objects.equal(affected.getPrefer(), oldPreferred) || !Objects.equal(affected.getProvidedBy(), oldProvided)) {
                        resetSelectionForAffectedCapabilities(affected);
                    }
                }

                private void resetSelectionForAffectedCapabilities(CapabilityInternal affected) {
                    for (ModuleIdentifier module : affected.getProvidedBy()) {
                        ComponentState selected = resolveState.getModule(module).getSelected();
                        if (selected != null) {
                            List<NodeState> nodes = selected.getNodes();
                            List<ComponentState> unattachedDependencies = selected.getUnattachedDependencies();
                            for (ComponentState unattachedDependency : unattachedDependencies) {
                                for (NodeState state : unattachedDependency.getNodes()) {
                                    state.resetSelectionState();
                                }
                            }
                            resolveState.getDeselectVersionAction().execute(module);
                            for (NodeState node : nodes) {
                                List<EdgeState> incomingEdges = node.getIncomingEdges();
                                for (EdgeState incomingEdge : incomingEdges) {
                                    incomingEdge.from.resetSelectionState();
                                }
                            }
                        }
                    }
                }
            });

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
        return dependencyState.getRequested();
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
}
