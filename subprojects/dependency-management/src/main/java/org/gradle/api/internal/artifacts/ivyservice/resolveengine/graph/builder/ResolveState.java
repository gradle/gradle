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
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.internal.artifacts.ComponentSelectorConverter;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.dsl.ModuleReplacementsData;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.specs.Spec;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.result.ComponentResolveResult;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

/**
 * Global resolution state.
 */
class ResolveState {
    private final Spec<? super DependencyMetadata> edgeFilter;
    private final Map<ModuleIdentifier, ModuleResolveState> modules = new LinkedHashMap<ModuleIdentifier, ModuleResolveState>();
    private final Map<ResolvedConfigurationIdentifier, NodeState> nodes = new LinkedHashMap<ResolvedConfigurationIdentifier, NodeState>();
    private final Map<ComponentSelector, SelectorState> selectors = new LinkedHashMap<ComponentSelector, SelectorState>();
    private final RootNode root;
    private final IdGenerator<Long> idGenerator;
    private final DependencyToComponentIdResolver idResolver;
    private final ComponentMetaDataResolver metaDataResolver;
    private final Set<NodeState> queued = Sets.newHashSet();
    private final LinkedList<NodeState> queue = new LinkedList<NodeState>();
    private final AttributesSchemaInternal attributesSchema;
    private final ModuleExclusions moduleExclusions;
    private final DeselectVersionAction deselectVersionAction = new DeselectVersionAction(this);
    private final ReplaceSelectionWithConflictResultAction replaceSelectionWithConflictResultAction;
    private final ModuleReplacementsData moduleReplacementsData;
    private final ComponentSelectorConverter componentSelectorConverter;

    public ResolveState(IdGenerator<Long> idGenerator, ComponentResolveResult rootResult, String rootConfigurationName, DependencyToComponentIdResolver idResolver,
                        ComponentMetaDataResolver metaDataResolver, Spec<? super DependencyMetadata> edgeFilter, AttributesSchemaInternal attributesSchema,
                        ModuleExclusions moduleExclusions, ModuleReplacementsData moduleReplacementsData,
                        ComponentSelectorConverter componentSelectorConverter) {
        this.idGenerator = idGenerator;
        this.idResolver = idResolver;
        this.metaDataResolver = metaDataResolver;
        this.edgeFilter = edgeFilter;
        this.attributesSchema = attributesSchema;
        this.moduleExclusions = moduleExclusions;
        this.moduleReplacementsData = moduleReplacementsData;
        this.componentSelectorConverter = componentSelectorConverter;
        ComponentState rootVersion = getRevision(rootResult.getId());
        rootVersion.setMetaData(rootResult.getMetaData());
        root = new RootNode(idGenerator.generateId(), rootVersion, new ResolvedConfigurationIdentifier(rootVersion.getId(), rootConfigurationName), this);
        nodes.put(root.getResolvedConfigurationId(), root);
        root.getComponent().getModule().select(root.getComponent());
        this.replaceSelectionWithConflictResultAction = new ReplaceSelectionWithConflictResultAction(this);
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
        ModuleResolveState module = modules.get(id);
        if (module == null) {
            module = new ModuleResolveState(idGenerator, id, metaDataResolver);
            modules.put(id, module);
        }
        return module;
    }

    public ComponentState getRevision(ModuleVersionIdentifier id) {
        return getModule(id.getModule()).getVersion(id);
    }

    public Collection<NodeState> getNodes() {
        return nodes.values();
    }

    public NodeState getNode(ComponentState module, ConfigurationMetadata configurationMetadata) {
        ResolvedConfigurationIdentifier id = new ResolvedConfigurationIdentifier(module.getId(), configurationMetadata.getName());
        NodeState configuration = nodes.get(id);
        if (configuration == null) {
            configuration = new NodeState(idGenerator.generateId(), id, module, this, configurationMetadata);
            nodes.put(id, configuration);
        }
        return configuration;
    }

    public Collection<SelectorState> getSelectors() {
        return selectors.values();
    }

    public SelectorState getSelector(DependencyMetadata dependencyMetadata, ModuleIdentifier moduleIdentifier) {
        ComponentSelector requested = dependencyMetadata.getSelector();
        SelectorState resolveState = selectors.get(requested);
        if (resolveState == null) {
            resolveState = new SelectorState(idGenerator.generateId(), dependencyMetadata, idResolver, this, moduleIdentifier);
            selectors.put(requested, resolveState);
        }
        return resolveState;
    }

    public NodeState peek() {
        return queue.isEmpty() ? null : queue.getFirst();
    }

    public NodeState pop() {
        NodeState next = queue.removeFirst();
        queued.remove(next);
        return next;
    }

    /**
     * Called when a change is made to a configuration node, such that its dependency graph <em>may</em> now be larger than it previously was, and the node should be visited.
     */
    public void onMoreSelected(NodeState node) {
        // Add to the end of the queue, so that we traverse the graph in breadth-wise order to pick up as many conflicts as
        // possible before attempting to resolve them
        if (queued.add(node)) {
            queue.addLast(node);
        }
    }

    /**
     * Called when a change is made to a configuration node, such that its dependency graph <em>may</em> now be smaller than it previously was, and the node should be visited.
     */
    public void onFewerSelected(NodeState node) {
        // Add to the front of the queue, to flush out configurations that are no longer required.
        if (queued.add(node)) {
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

    public ModuleReplacementsData getModuleReplacementsData() {
        return moduleReplacementsData;
    }

    public ComponentSelectorConverter getComponentSelectorConverter() {
        return componentSelectorConverter;
    }
}
