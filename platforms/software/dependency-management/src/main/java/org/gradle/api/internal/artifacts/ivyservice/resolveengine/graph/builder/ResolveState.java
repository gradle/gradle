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

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.ComponentSelectorConverter;
import org.gradle.api.internal.artifacts.ComponentVariantNodeIdentifier;
import org.gradle.api.internal.artifacts.NodeIdentifier;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.configurations.ConflictResolution;
import org.gradle.api.internal.artifacts.dependencies.DefaultResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionApplicator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.Version;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.RootComponentMetadataBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.CapabilitiesConflictHandler;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.ModuleConflictHandler;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.selectors.ComponentStateFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.selectors.SelectorStateResolver;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.AttributeSchemaServices;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.api.specs.Spec;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.ComponentGraphSpecificResolveState;
import org.gradle.internal.component.model.ComponentIdGenerator;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.GraphVariantSelector;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Global resolution state.
 */
public class ResolveState implements ComponentStateFactory<ComponentState> {
    private final Spec<? super DependencyMetadata> edgeFilter;
    private final Map<ModuleIdentifier, ModuleResolveState> modules;
    private final Map<NodeIdentifier, NodeState> nodes;
    private final Map<SelectorCacheKey, SelectorState> selectors;
    private final RootNode root;
    private final ComponentIdGenerator idGenerator;
    private final DependencyToComponentIdResolver idResolver;
    private final ComponentMetaDataResolver metaDataResolver;
    private final Deque<NodeState> queue;
    private final ConflictResolution conflictResolution;
    private final ImmutableAttributesSchema consumerSchema;
    private final ModuleExclusions moduleExclusions;
    private final DeselectVersionAction deselectVersionAction = new DeselectVersionAction(this);
    private final ReplaceSelectionWithConflictResultAction replaceSelectionWithConflictResultAction;
    private final ComponentSelectorConverter componentSelectorConverter;
    private final AttributesFactory attributesFactory;
    private final AttributeSchemaServices attributeSchemaServices;
    private final DependencySubstitutionApplicator dependencySubstitutionApplicator;
    private final VersionSelectorScheme versionSelectorScheme;
    private final Comparator<Version> versionComparator;
    private final VersionParser versionParser;
    private final SelectorStateResolver<ComponentState> selectorStateResolver;
    private final ResolveOptimizations resolveOptimizations;
    private final Map<VersionConstraint, ResolvedVersionConstraint> resolvedVersionConstraints = new HashMap<>();
    private final AttributeDesugaring attributeDesugaring;
    private final ModuleConflictHandler moduleConflictHandler;
    private final CapabilitiesConflictHandler capabilitiesConflictHandler;
    private final GraphVariantSelector variantSelector;

    public ResolveState(
        ComponentIdGenerator idGenerator,
        RootComponentMetadataBuilder.RootComponentState root,
        DependencyToComponentIdResolver idResolver,
        ComponentMetaDataResolver metaDataResolver,
        Spec<? super DependencyMetadata> edgeFilter,
        ModuleExclusions moduleExclusions,
        ComponentSelectorConverter componentSelectorConverter,
        AttributesFactory attributesFactory,
        AttributeSchemaServices attributeSchemaServices,
        AttributeDesugaring attributeDesugaring,
        DependencySubstitutionApplicator dependencySubstitutionApplicator,
        VersionSelectorScheme versionSelectorScheme,
        VersionComparator versionComparator,
        VersionParser versionParser,
        ConflictResolution conflictResolution,
        List<? extends DependencyMetadata> syntheticDependencies,
        ModuleConflictHandler moduleConflictHandler,
        CapabilitiesConflictHandler capabilitiesConflictHandler,
        GraphVariantSelector variantSelector
    ) {
        this.idGenerator = idGenerator;
        this.idResolver = idResolver;
        this.metaDataResolver = metaDataResolver;
        this.edgeFilter = edgeFilter;
        this.moduleExclusions = moduleExclusions;
        this.componentSelectorConverter = componentSelectorConverter;
        this.attributesFactory = attributesFactory;
        this.attributeSchemaServices = attributeSchemaServices;
        this.dependencySubstitutionApplicator = dependencySubstitutionApplicator;
        this.versionSelectorScheme = versionSelectorScheme;
        this.versionComparator = versionComparator.asVersionComparator();
        this.versionParser = versionParser;
        this.conflictResolution = conflictResolution;
        this.moduleConflictHandler = moduleConflictHandler;
        this.capabilitiesConflictHandler = capabilitiesConflictHandler;
        this.resolveOptimizations = new ResolveOptimizations();
        this.attributeDesugaring = attributeDesugaring;
        this.replaceSelectionWithConflictResultAction = new ReplaceSelectionWithConflictResultAction(this);
        this.variantSelector = variantSelector;

        LocalComponentGraphResolveState rootComponentState = root.getRootComponent();
        VariantGraphResolveState rootVariant = root.getRootVariant();
        ModuleVersionIdentifier rootModuleVersionId = rootComponentState.getModuleVersionId();
        ComponentIdentifier rootComponentId = rootComponentState.getId();
        this.consumerSchema = rootComponentState.getMetadata().getAttributesSchema();

        int graphSize = estimateGraphSize(rootVariant);
        this.modules = new LinkedHashMap<>(graphSize);
        this.nodes = new LinkedHashMap<>(3 * graphSize / 2);
        this.selectors = new LinkedHashMap<>(5 * graphSize / 2);
        this.queue = new ArrayDeque<>(graphSize);

        // Create root component and module
        ModuleResolveState rootModule = getModule(rootModuleVersionId.getModule(), true);
        ComponentState rootComponent = rootModule.getVersion(rootModuleVersionId, rootComponentId);
        rootComponent.setRoot();
        rootComponent.setState(rootComponentState, ComponentGraphSpecificResolveState.EMPTY_STATE);
        rootModule.select(rootComponent);

        this.selectorStateResolver = new SelectorStateResolver<>(moduleConflictHandler.getResolver(), this, rootComponent, resolveOptimizations, this.versionComparator, versionParser);
        rootModule.setSelectorStateResolver(selectorStateResolver);

        // Create root node
        this.root = new RootNode(idGenerator.nextGraphNodeId(), rootComponent, this, syntheticDependencies, rootVariant);
        rootComponent.addNode(this.root);
        ComponentVariantNodeIdentifier rootNodeId = new ComponentVariantNodeIdentifier(rootComponentId, rootVariant.getName());
        nodes.put(rootNodeId, this.root);
    }

    public ComponentIdGenerator getIdGenerator() {
        return idGenerator;
    }

    public ModuleConflictHandler getModuleConflictHandler() {
        return moduleConflictHandler;
    }

    public CapabilitiesConflictHandler getCapabilitiesConflictHandler() {
        return capabilitiesConflictHandler;
    }

    public Collection<ModuleResolveState> getModules() {
        return modules.values();
    }

    Spec<? super DependencyMetadata> getEdgeFilter() {
        return edgeFilter;
    }

    RootNode getRoot() {
        return root;
    }

    public ModuleResolveState getModule(ModuleIdentifier id) {
        return getModule(id, false);
    }

    public ComponentMetaDataResolver getComponentMetadataResolver() {
        return metaDataResolver;
    }

    private ModuleResolveState getModule(ModuleIdentifier id, boolean rootModule) {
        return modules.computeIfAbsent(id, mid -> new ModuleResolveState(idGenerator, id, metaDataResolver, attributesFactory, versionComparator, versionParser, selectorStateResolver, resolveOptimizations, rootModule, conflictResolution));
    }

    @Override
    public ComponentState getRevision(ComponentIdentifier componentIdentifier, ModuleVersionIdentifier id, ComponentGraphResolveState state, ComponentGraphSpecificResolveState graphState) {
        ComponentState componentState = getModule(id.getModule()).getVersion(id, componentIdentifier);
        if (!componentState.alreadyResolved()) {
            componentState.setState(state, graphState);
        }
        return componentState;
    }

    public Collection<NodeState> getNodes() {
        return nodes.values();
    }

    public NodeState getNode(ComponentState component, VariantGraphResolveState variant, boolean selectedByVariantAwareResolution) {
        ComponentVariantNodeIdentifier id = new ComponentVariantNodeIdentifier(component.getComponentId(), variant.getName());
        return nodes.computeIfAbsent(id, rci -> {
            NodeState node = new NodeState(idGenerator.nextGraphNodeId(), component, this, variant, selectedByVariantAwareResolution);
            component.addNode(node);
            return node;
        });
    }

    public Collection<SelectorState> getSelectors() {
        return selectors.values();
    }

    public SelectorState computeSelectorFor(DependencyState dependencyState, boolean ignoreVersion) {
        boolean isVirtualPlatformEdge = dependencyState.getDependency() instanceof LenientPlatformDependencyMetadata;
        SelectorState selectorState = selectors.computeIfAbsent(new SelectorCacheKey(dependencyState.getRequested(), ignoreVersion, isVirtualPlatformEdge), req -> {
            ModuleIdentifier moduleIdentifier = dependencyState.getModuleIdentifier();
            return new SelectorState(idGenerator.nextGraphNodeId(), dependencyState, idResolver, this, moduleIdentifier, ignoreVersion);
        });
        selectorState.update(dependencyState);
        return selectorState;
    }

    @Nullable
    public NodeState peek() {
        return queue.isEmpty() ? null : queue.getFirst();
    }

    public NodeState pop() {
        NodeState next = queue.removeFirst();
        return next.dequeue();
    }

    /**
     * Called when a change is made to a configuration node, such that its dependency graph <em>may</em> now be larger than it previously was, and the node should be visited.
     */
    public void onMoreSelected(NodeState node) {
        // Add to the end of the queue, so that we traverse the graph in breadth-wise order to pick up as many conflicts as
        // possible before attempting to resolve them
        if (node.enqueue()) {
            queue.addLast(node);
        }
    }

    /**
     * Called when a change is made to a configuration node, such that its dependency graph <em>may</em> now be smaller than it previously was, and the node should be visited.
     */
    public void onFewerSelected(NodeState node) {
        // Add to the front of the queue, to flush out configurations that are no longer required.
        if (node.enqueue()) {
            queue.addFirst(node);
        }
    }

    public ImmutableAttributesSchema getConsumerSchema() {
        return consumerSchema;
    }

    public ModuleExclusions getModuleExclusions() {
        return moduleExclusions;
    }

    public DeselectVersionAction getDeselectVersionAction() {
        return deselectVersionAction;
    }

    public ReplaceSelectionWithConflictResultAction getReplaceSelectionWithConflictResultAction() {
        return replaceSelectionWithConflictResultAction;
    }

    public ComponentSelectorConverter getComponentSelectorConverter() {
        return componentSelectorConverter;
    }

    public AttributesFactory getAttributesFactory() {
        return attributesFactory;
    }

    public AttributeSchemaServices getAttributeSchemaServices() {
        return attributeSchemaServices;
    }

    public DependencySubstitutionApplicator getDependencySubstitutionApplicator() {
        return dependencySubstitutionApplicator;
    }

    PendingDependenciesVisitor newPendingDependenciesVisitor() {
        return new DefaultPendingDependenciesVisitor(this);
    }

    @Nullable
    ResolvedVersionConstraint resolveVersionConstraint(ComponentSelector selector) {
        if (selector instanceof ModuleComponentSelector) {
            return resolveVersionConstraint(((ModuleComponentSelector) selector).getVersionConstraint());
        }
        return null;
    }

    ResolvedVersionConstraint resolveVersionConstraint(VersionConstraint vc) {
        return resolvedVersionConstraints.computeIfAbsent(vc, key -> new DefaultResolvedVersionConstraint(key, versionSelectorScheme));
    }

    ComponentSelector desugarSelector(ComponentSelector requested) {
        return attributeDesugaring.desugarSelector(requested);
    }

    AttributeDesugaring getAttributeDesugaring() {
        return attributeDesugaring;
    }

    ResolveOptimizations getResolveOptimizations() {
        return resolveOptimizations;
    }

    public GraphVariantSelector getVariantSelector() {
        return variantSelector;
    }

    private static class SelectorCacheKey {
        private final ComponentSelector componentSelector;
        private final boolean ignoreVersion;
        private final boolean virtualPlatformEdge;

        private SelectorCacheKey(ComponentSelector componentSelector, boolean ignoreVersion, boolean virtualPlatformEdge) {
            this.componentSelector = componentSelector;
            this.ignoreVersion = ignoreVersion;
            this.virtualPlatformEdge = virtualPlatformEdge;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SelectorCacheKey that = (SelectorCacheKey) o;
            return ignoreVersion == that.ignoreVersion &&
                virtualPlatformEdge == that.virtualPlatformEdge &&
                componentSelector.equals(that.componentSelector);
        }

        @Override
        public int hashCode() {
            return Objects.hash(componentSelector, ignoreVersion, virtualPlatformEdge);
        }
    }

    /**
     * This method is a heuristic that gives an idea of the "size" of the graph. The larger
     * the graph is, the higher the risk of internal resizes exists, so we try to estimate
     * the size of the graph to avoid maps resizing.
     */
    private static int estimateGraphSize(VariantGraphResolveState rootVariant) {
        int numDependencies = rootVariant.getDependencies().size();

        // TODO #24641: Why are the numbers and operations here the way they are?
        //  Are they up-to-date? We should be able to test if these values are still optimal.
        int estimate = (int) (512 * Math.log(numDependencies));
        return Math.max(10, estimate);
    }
}
