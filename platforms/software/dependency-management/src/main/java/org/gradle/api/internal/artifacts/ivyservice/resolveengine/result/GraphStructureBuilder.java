/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.internal.artifacts.NamedVariantIdentifier;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.model.DefaultImmutableCapability;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.model.VariantIdentifier;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Responsible for building a {@link GraphStructure} from raw graph data.
 * <p>
 * This builder constructs a memory-efficient, CC-serializable, immutable representation
 * of the graph, which can be used directly as a data source for internal consumers or
 * as a backing structure for various public APIs.
 */
public class GraphStructureBuilder {

    private long rootNodeId;

    // Nodes
    private final Long2IntMap nodeIdToIndex = new Long2IntOpenHashMap();
    private final IntArrayList nodeOwnerIndices = new IntArrayList();
    private final List<String> nodeVariantNames = new ArrayList<>();
    private final List<VariantIdentifier> nodeVariantIds = new ArrayList<>();
    private final List<ImmutableAttributes> nodeAttributes = new ArrayList<>();
    private final List<ImmutableCapabilities> nodeCapabilities = new ArrayList<>();
    private final Int2LongMap externalVariantIds = new Int2LongOpenHashMap();

    // Edges
    private final IntArrayList edgeIndices = new IntArrayList();
    private final BitSet edgeConstraints = new BitSet();
    private final List<ComponentSelector> edgeSelectors = new ArrayList<>();
    private final LongList edgeTargetNodeIds = new LongArrayList();
    private final Int2ObjectMap<GraphStructure.Edges.EdgeFailure> edgeFailureMap = new Int2ObjectOpenHashMap<>();

    // Components
    private final Long2IntMap componentIdToIndex = new Long2IntOpenHashMap();
    private final List<ComponentSelectionReasonInternal> componentSelectionReasons = new ArrayList<>();
    private final ArrayList<@Nullable String> componentRepositoryNames = new ArrayList<>();
    private final List<ComponentIdentifier> componentIds = new ArrayList<>();
    private final List<ModuleVersionIdentifier> componentModuleVersionIds = new ArrayList<>();

    public GraphStructureBuilder() {
        componentIdToIndex.defaultReturnValue(-1);
        nodeIdToIndex.defaultReturnValue(-1);
    }

    /**
     * Create a minimal graph containing only a root component and a
     * root variant, with no other outgoing edges.
     */
    public static GraphStructure empty(
        ModuleVersionIdentifier id,
        ComponentIdentifier componentIdentifier,
        ImmutableAttributes attributes,
        ImmutableCapabilities rawCapabilities,
        String rootVariantName,
        AttributeDesugaring attributeDesugaring
    ) {
        GraphStructureBuilder builder = new GraphStructureBuilder();
        builder.start(1L);
        builder.addComponent(0L, ComponentSelectionReasons.root(), null, componentIdentifier, id);
        builder.addNode(
            1L,
            0L,
            rootVariantName,
            new NamedVariantIdentifier(componentIdentifier, rootVariantName),
            attributeDesugaring.desugar(attributes),
            rawCapabilities,
            -1
        );

        return builder.build();
    }

    /**
     * Must be called before any other methods.
     */
    public void start(long rootNodeId) {
        this.rootNodeId = rootNodeId;
    }

    /**
     * Add a component to the graph. Must be called before any nodes belonging to the
     * component are added. The order of components added by this method is preserved
     * so that the component added by the nth call to this method is addressable by the
     * nth component in the resulting graph structure.
     */
    public void addComponent(
        long id,
        ComponentSelectionReasonInternal selectionReason,
        @Nullable String repositoryName,
        ComponentIdentifier componentId,
        ModuleVersionIdentifier moduleVersionId
    ) {
        componentIdToIndex.put(id, componentIds.size());
        componentSelectionReasons.add(selectionReason);
        componentRepositoryNames.add(repositoryName);
        componentIds.add(componentId);
        componentModuleVersionIds.add(moduleVersionId);
    }

    /**
     * Adds a node to the graph. Must be called after its owning component has been
     * added. The order of nodes added by this method is preserved so that the node
     * added by the nth call to this method is addressable by the nth node in
     * the resulting graph structure.
     */
    public void addNode(
        long id,
        long ownerId,
        String variantName,
        VariantIdentifier variantId,
        ImmutableAttributes attributes,
        ImmutableCapabilities rawCapabilities,
        long externalVariantId
    ) {
        int nodeIndex = nodeOwnerIndices.size();
        nodeIdToIndex.put(id, nodeIndex);

        int ownerIndex = componentIdToIndex.get(ownerId);
        if (ownerIndex == -1) {
            throw new IllegalStateException("Cannot find owner component for node " + id + " with owner " + ownerId);
        }
        nodeOwnerIndices.add(ownerIndex);
        nodeVariantNames.add(variantName);
        nodeVariantIds.add(variantId);
        nodeAttributes.add(attributes);
        nodeCapabilities.add(capabilitiesFor(rawCapabilities, ownerIndex));
        if (externalVariantId != -1) {
            externalVariantIds.put(nodeIndex, externalVariantId);
        }
        edgeIndices.add(edgeSelectors.size());
    }

    /**
     * Adds a successful edge targeting the given node. The source node is
     * attributed to the last added node.
     */
    public void addSuccessfulEdge(
        ComponentSelector selector,
        boolean constraint,
        long targetNodeId
    ) {
        int edgeIndex = edgeSelectors.size();
        edgeSelectors.add(selector);
        edgeConstraints.set(edgeIndex, constraint);
        edgeTargetNodeIds.add(targetNodeId);
    }

    /**
     * Adds a failed edge targeting the given node. The source node is
     * attributed to the last added node.
     */
    public void addFailedEdge(
        ComponentSelector selector,
        boolean constraint,
        ComponentSelectionReasonInternal reason,
        ModuleVersionResolveException failure
    ) {
        int edgeIndex = edgeSelectors.size();
        edgeSelectors.add(selector);
        edgeConstraints.set(edgeIndex, constraint);
        edgeTargetNodeIds.add(-1);
        edgeFailureMap.put(edgeIndex, new GraphStructure.Edges.EdgeFailure(
            failure,
            reason
        ));
    }

    // TODO: Would probably be better if a node already knew its real capabilities instead of
    // correcting for them at the API surface.
    private ImmutableCapabilities capabilitiesFor(ImmutableCapabilities capabilities, int ownerIndex) {
        if (!capabilities.isEmpty()) {
            return capabilities;
        }

        ModuleVersionIdentifier moduleVersionId = componentModuleVersionIds.get(ownerIndex);
        return new ImmutableCapabilities(ImmutableSet.of(DefaultImmutableCapability.defaultCapabilityForComponent(moduleVersionId)));
    }

    /**
     * Builds the finalized graph structure. Calling any other method on this builder
     * after this method has been called will result in undefined behavior.
     */
    public GraphStructure build() {
        edgeIndices.add(edgeSelectors.size());

        Int2IntMap externalVariantIndices = new Int2IntOpenHashMap(externalVariantIds.size());
        for (Int2LongMap.Entry entry : externalVariantIds.int2LongEntrySet()) {
            int externalVariantIndex = nodeIdToIndex.get(entry.getLongValue());
            if (externalVariantIndex == -1) {
                throw new IllegalStateException("Cannot find node for external variant " + entry.getLongValue());
            }
            externalVariantIndices.put(entry.getIntKey(), externalVariantIndex);
        }

        IntList edgeTargetNodeIndices = new IntArrayList(edgeTargetNodeIds.size());
        for (int i = 0; i < edgeTargetNodeIds.size(); i++) {
            long targetNodeId = edgeTargetNodeIds.getLong(i);
            if (targetNodeId != -1) {
                int targetNodeIndex = nodeIdToIndex.get(targetNodeId);
                if (targetNodeIndex == -1) {
                    throw new IllegalStateException("Cannot find node index for target node " + targetNodeId);
                }
                edgeTargetNodeIndices.add(targetNodeIndex);
            } else {
                edgeTargetNodeIndices.add(-1);
            }
        }

        int rootNodeIndex = nodeIdToIndex.get(rootNodeId);

        // Trim the lists to the actual size to save memory.
        // The below ImmutableList.copy also serves this same purpose.
        nodeOwnerIndices.trim();
        edgeIndices.trim();
        componentRepositoryNames.trimToSize();

        return new DefaultGraphStructure(
            new DefaultNodes(
                rootNodeIndex,
                nodeOwnerIndices,
                ImmutableList.copyOf(nodeVariantNames),
                ImmutableList.copyOf(nodeVariantIds),
                ImmutableList.copyOf(nodeAttributes),
                ImmutableList.copyOf(nodeCapabilities),
                externalVariantIndices
            ),
            new DefaultEdges(
                edgeIndices,
                ImmutableList.copyOf(edgeSelectors),
                edgeConstraints,
                edgeTargetNodeIndices,
                edgeFailureMap
            ),
            new DefaultComponents(
                ImmutableList.copyOf(componentSelectionReasons),
                componentRepositoryNames,
                ImmutableList.copyOf(componentIds),
                ImmutableList.copyOf(componentModuleVersionIds)
            )
        );

    }

    private record DefaultNodes(
        int root,
        IntList owners,
        ImmutableList<String> variantNames,
        ImmutableList<VariantIdentifier> variantIds,
        ImmutableList<ImmutableAttributes> attributes,
        ImmutableList<ImmutableCapabilities> capabilities,
        Int2IntMap externalVariantIndices
    ) implements GraphStructure.Nodes {

        @Override
        public int count() {
            return owners.size();
        }

        @Override
        public int owner(int index) {
            return owners.getInt(index);
        }

        @Override
        public String variantName(int index) {
            return variantNames.get(index);
        }

        @Override
        public VariantIdentifier id(int index) {
            return variantIds.get(index);
        }

        @Override
        public ImmutableAttributes attributes(int index) {
            return attributes.get(index);
        }

        @Override
        public ImmutableCapabilities capabilities(int index) {
            return capabilities.get(index);
        }

        @Override
        public int externalVariantIndex(int index) {
            return externalVariantIndices.getOrDefault(index, -1);
        }

    }

    private record DefaultEdges(
        IntList indices,
        ImmutableList<ComponentSelector> selectors,
        BitSet constraints,
        IntList targetNodeIndices,
        Int2ObjectMap<EdgeFailure> failures
    )  implements GraphStructure.Edges {

        @Override
        public int start(int nodeIndex) {
            return indices.getInt(nodeIndex);
        }

        @Override
        public int end(int nodeIndex) {
            return indices.getInt(nodeIndex + 1);
        }

        @Override
        public ComponentSelector selector(int index) {
            return selectors.get(index);
        }

        @Override
        public boolean constraint(int index) {
            return constraints.get(index);
        }

        @Override
        public int targetNode(int index) {
            return targetNodeIndices.getInt(index);
        }

        @Override
        public EdgeFailure failure(int index) {
            EdgeFailure failure = failures.get(index);
            if (failure == null) {
                throw new IllegalArgumentException("No failure for edge " + index);
            }
            return failure;
        }

    }

    private record DefaultComponents(
        ImmutableList<ComponentSelectionReasonInternal> selectionReasons,
        List<@Nullable String> repositoryNames,
        ImmutableList<ComponentIdentifier> ids,
        ImmutableList<ModuleVersionIdentifier> moduleVersionIds
    ) implements GraphStructure.Components {

        @Override
        public int count() {
            return ids.size();
        }

        @Override
        public ComponentIdentifier id(int componentIndex) {
            return ids.get(componentIndex);
        }

        @Override
        public ComponentSelectionReasonInternal selectionReason(int componentIndex) {
            return selectionReasons.get(componentIndex);
        }

        @Override
        public @Nullable String repositoryName(int componentIndex) {
            return repositoryNames.get(componentIndex);
        }

        @Override
        public ModuleVersionIdentifier moduleVersionId(int componentIndex) {
            return moduleVersionIds.get(componentIndex);
        }

    }

    private record DefaultGraphStructure(
        GraphStructure.Nodes nodes,
        GraphStructure.Edges edges,
        GraphStructure.Components components
    ) implements GraphStructure { }

}
