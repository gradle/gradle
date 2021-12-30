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

import com.google.common.collect.Maps;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.ComponentSelectorConverter;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.configurations.ConflictResolution;
import org.gradle.api.internal.artifacts.dependencies.DefaultResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionApplicator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.Version;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleConflictResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.selectors.ComponentStateFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.selectors.SelectorStateResolver;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.specs.Spec;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.result.ComponentResolveResult;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Global resolution state.
 */
class ResolveState implements ComponentStateFactory<ComponentState> {
    private final Spec<? super DependencyMetadata> edgeFilter;
    private final Map<ModuleIdentifier, ModuleResolveState> modules;
    private final Map<ResolvedConfigurationIdentifier, NodeState> nodes;
    private final Map<SelectorCacheKey, SelectorState> selectors;
    private final RootNode root;
    private final IdGenerator<Long> idGenerator;
    private final DependencyToComponentIdResolver idResolver;
    private final ComponentMetaDataResolver metaDataResolver;
    private final Deque<NodeState> queue;
    private final ConflictResolution conflictResolution;
    private final AttributesSchemaInternal attributesSchema;
    private final ModuleExclusions moduleExclusions;
    private final DeselectVersionAction deselectVersionAction = new DeselectVersionAction(this);
    private final ReplaceSelectionWithConflictResultAction replaceSelectionWithConflictResultAction;
    private final ComponentSelectorConverter componentSelectorConverter;
    private final ImmutableAttributesFactory attributesFactory;
    private final DependencySubstitutionApplicator dependencySubstitutionApplicator;
    private final VersionSelectorScheme versionSelectorScheme;
    private final Comparator<Version> versionComparator;
    private final VersionParser versionParser;
    private final SelectorStateResolver<ComponentState> selectorStateResolver;
    private final ResolveOptimizations resolveOptimizations;
    private final Map<VersionConstraint, ResolvedVersionConstraint> resolvedVersionConstraints = Maps.newHashMap();
    private final AttributeDesugaring attributeDesugaring;
    private final List<? extends DependencyMetadata> generatedRootDependencies;
    private final ResolutionConflictTracker conflictTracker;

    public ResolveState(
        IdGenerator<Long> idGenerator,
        ComponentResolveResult rootResult,
        String rootConfigurationName,
        DependencyToComponentIdResolver idResolver,
        ComponentMetaDataResolver metaDataResolver,
        Spec<? super DependencyMetadata> edgeFilter,
        AttributesSchemaInternal attributesSchema,
        ModuleExclusions moduleExclusions,
        ComponentSelectorConverter componentSelectorConverter,
        ImmutableAttributesFactory attributesFactory,
        DependencySubstitutionApplicator dependencySubstitutionApplicator,
        VersionSelectorScheme versionSelectorScheme,
        Comparator<Version> versionComparator,
        VersionParser versionParser,
        ModuleConflictResolver<ComponentState> conflictResolver,
        int graphSize,
        ConflictResolution conflictResolution,
        List<? extends DependencyMetadata> generatedRootDependencies,
        ResolutionConflictTracker conflictTracker
    ) {
        this.idGenerator = idGenerator;
        this.idResolver = idResolver;
        this.metaDataResolver = metaDataResolver;
        this.edgeFilter = edgeFilter;
        this.attributesSchema = attributesSchema;
        this.moduleExclusions = moduleExclusions;
        this.componentSelectorConverter = componentSelectorConverter;
        this.attributesFactory = attributesFactory;
        this.dependencySubstitutionApplicator = dependencySubstitutionApplicator;
        this.versionSelectorScheme = versionSelectorScheme;
        this.versionComparator = versionComparator;
        this.versionParser = versionParser;
        this.modules = new LinkedHashMap<>(graphSize);
        this.nodes = new LinkedHashMap<>(3 * graphSize / 2);
        this.selectors = new LinkedHashMap<>(5 * graphSize / 2);
        this.queue = new ArrayDeque<>(graphSize);
        this.conflictResolution = conflictResolution;
        this.generatedRootDependencies = generatedRootDependencies;
        this.conflictTracker = conflictTracker;
        this.resolveOptimizations = new ResolveOptimizations();
        this.attributeDesugaring = new AttributeDesugaring(attributesFactory);
        // Create root module
        getModule(rootResult.getModuleVersionId().getModule(), true);
        ComponentState rootVersion = getRevision(rootResult.getId(), rootResult.getModuleVersionId(), rootResult.getMetadata());
        final ResolvedConfigurationIdentifier id = new ResolvedConfigurationIdentifier(rootVersion.getId(), rootConfigurationName);
        ConfigurationMetadata configurationMetadata = rootVersion.getMetadata().getConfiguration(id.getConfiguration());
        root = new RootNode(idGenerator.generateId(), rootVersion, id, this, configurationMetadata);
        nodes.put(root.getResolvedConfigurationId(), root);
        root.getComponent().getModule().select(root.getComponent());
        this.replaceSelectionWithConflictResultAction = new ReplaceSelectionWithConflictResultAction(this);
        selectorStateResolver = new SelectorStateResolver<>(conflictResolver, this, rootVersion, resolveOptimizations, versionComparator);
        getModule(rootResult.getModuleVersionId().getModule()).setSelectorStateResolver(selectorStateResolver);
    }

    public ResolutionConflictTracker getConflictTracker() {
        return conflictTracker;
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

    private ModuleResolveState getModule(ModuleIdentifier id, boolean rootModule) {
        return modules.computeIfAbsent(id, mid -> new ModuleResolveState(idGenerator, id, metaDataResolver, attributesFactory, versionComparator, versionParser, selectorStateResolver, resolveOptimizations, rootModule, conflictResolution));
    }

    List<? extends DependencyMetadata> getGeneratedRootDependencies() {
        return generatedRootDependencies;
    }

    @Override
    public ComponentState getRevision(ComponentIdentifier componentIdentifier, ModuleVersionIdentifier id, ComponentResolveMetadata metadata) {
        ComponentState componentState = getModule(id.getModule()).getVersion(id, componentIdentifier);
        if (!componentState.alreadyResolved()) {
            componentState.setMetadata(metadata);
        }
        return componentState;
    }

    public Collection<NodeState> getNodes() {
        return nodes.values();
    }

    public NodeState getNode(ComponentState module, ConfigurationMetadata configurationMetadata) {
        ResolvedConfigurationIdentifier id = new ResolvedConfigurationIdentifier(module.getId(), configurationMetadata.getName());
        return nodes.computeIfAbsent(id, rci -> new NodeState(idGenerator.generateId(), id, module, this, configurationMetadata));
    }

    public Collection<SelectorState> getSelectors() {
        return selectors.values();
    }

    public SelectorState getSelector(DependencyState dependencyState, boolean ignoreVersion) {
        boolean isVirtualPlatformEdge = dependencyState.getDependency() instanceof LenientPlatformDependencyMetadata;
        SelectorState selectorState = selectors.computeIfAbsent(new SelectorCacheKey(dependencyState.getRequested(), ignoreVersion, isVirtualPlatformEdge), req -> {
            ModuleIdentifier moduleIdentifier = dependencyState.getModuleIdentifier();
            return new SelectorState(idGenerator.generateId(), dependencyState, idResolver, this, moduleIdentifier, ignoreVersion);
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

    public AttributesSchemaInternal getAttributesSchema() {
        return attributesSchema;
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

    public ImmutableAttributesFactory getAttributesFactory() {
        return attributesFactory;
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

    ImmutableAttributes desugar(ImmutableAttributes attributes) {
        return attributeDesugaring.desugar(attributes);
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

}
