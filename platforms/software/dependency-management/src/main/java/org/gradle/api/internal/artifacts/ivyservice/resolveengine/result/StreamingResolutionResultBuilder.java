/*
 * Copyright 2013 the original author or authors.
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
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionCause;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ModuleVersionIdentifierSerializer;
import org.gradle.api.internal.artifacts.capability.CapabilitySelectorSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphComponent;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.RootGraphNode;
import org.gradle.api.internal.artifacts.result.DefaultResolvedVariantResult;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.cache.internal.BinaryStore;
import org.gradle.cache.internal.Store;
import org.gradle.internal.Describables;
import org.gradle.internal.component.external.model.DefaultImmutableCapability;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.caching.DesugaringAttributeContainerSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.gradle.internal.UncheckedException.throwAsUncheckedException;

public class StreamingResolutionResultBuilder implements DependencyGraphVisitor {

    private final static byte COMPONENT = 1;
    private final static byte NODE = 2;
    private final static byte END = 3;

    private final BinaryStore store;
    private final Store<GraphStructure> cache;
    private final ThisBuildTreeOnlyGraphElementStore graphElementStore;
    private final AttributeDesugaring attributeDesugaring;

    // Serializers
    private final Supplier<StatefulSerializers> statefulSerializersFactory;
    private final ComponentSelectionReasonSerializer reasonSerializer;
    private final Serializer<ComponentIdentifier> componentIdSerializer;
    private final AttributeContainerSerializer attributeContainerSerializer;
    private final ImmutableCapabilitiesSerializer capabilitySerializer;
    private final Serializer<ModuleVersionIdentifier> moduleVersionIdSerializer;
    private final Serializer<ComponentSelector> componentSelectorSerializer;

    // State
    private boolean mayHaveVirtualPlatforms;
    private @Nullable ImmutableAttributes rootAttributes;
    private final LongSet visitedComponents = new LongOpenHashSet();
    private final List<ModuleVersionResolveException> failures = new ArrayList<>();
    private @Nullable List<List<ResolvedVariantResult>> allSelectableVariantResults;

    public StreamingResolutionResultBuilder(
        BinaryStore store,
        Store<GraphStructure> cache,
        ThisBuildTreeOnlyGraphElementStore graphElementStore,
        AttributeDesugaring attributeDesugaring,
        CapabilitySelectorSerializer capabilitySelectorSerializer,
        ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        AttributesFactory attributeFactory,
        NamedObjectInstantiator namedDomainObjectInstantiator,
        boolean includeAllSelectableVariantResults
    ) {
        this.store = store;
        this.cache = cache;
        this.graphElementStore = graphElementStore;
        this.attributeDesugaring = attributeDesugaring;

        // These deduplicating serializers reduce the size overhead of the serialized
        // graphs and their de-serialized in-memory representation.
        // However, since they are stateful, we must create a new instance each time we
        // serialize and deserialize a graph.
        this.statefulSerializersFactory = () -> StatefulSerializers.create(
            capabilitySelectorSerializer,
            attributeFactory,
            namedDomainObjectInstantiator
        );

        StatefulSerializers statefulSerializers = statefulSerializersFactory.get();
        this.reasonSerializer = new ComponentSelectionReasonSerializer(componentSelectionDescriptorFactory);
        this.componentIdSerializer = new ComponentIdentifierSerializer();
        this.attributeContainerSerializer = statefulSerializers.attributeContainerSerializer();
        this.capabilitySerializer = new ImmutableCapabilitiesSerializer();
        this.moduleVersionIdSerializer = new ModuleVersionIdentifierSerializer(moduleIdentifierFactory);
        this.componentSelectorSerializer = statefulSerializers.componentSelectorSerializer();

        if (includeAllSelectableVariantResults) {
            this.allSelectableVariantResults = new ArrayList<>();
        }
    }

    public ResolvedDependencyGraph getResolvedDependencyGraph(Set<UnresolvedDependency> dependencyLockingFailures) {
        BinaryStore.BinaryData data = store.done();
        GraphFactory graphSource = new GraphFactory(data, cache, failures, dependencyLockingFailures, () -> {
            StatefulSerializers statefulSerializers = statefulSerializersFactory.get();
            return new GraphDeserializer(
                statefulSerializers.componentSelectorSerializer(),
                statefulSerializers.attributeContainerSerializer(),
                graphElementStore,
                reasonSerializer,
                componentIdSerializer,
                capabilitySerializer,
                moduleVersionIdSerializer,
                attributeDesugaring
            );
        });
        assert rootAttributes != null : "Cannot get graph structure before graph is visited.";
        return new ResolvedDependencyGraph(rootAttributes, graphSource::create, allSelectableVariantResults);
    }

    @Override
    public void start(final RootGraphNode root) {
        this.rootAttributes = root.getMetadata().getAttributes();
        this.mayHaveVirtualPlatforms = root.getResolveOptimizations().mayHaveVirtualPlatforms();
        // TODO: We should write the size of the graph at the beginning of traversal
        // so we can initialize the GraphStructureBuilder to avoid resizes/copying
        store.write(encoder -> {
            encoder.writeSmallLong(root.getNodeId());
        });
    }

    @Override
    public void finish(final RootGraphNode root) {
        store.write(encoder -> {
            encoder.writeByte(END);
        });
    }

    @Override
    public void visitEdges(DependencyGraphNode node) {
        store.write(encoder -> {
            DependencyGraphComponent component = node.getOwner();
            boolean adhoc = component.getResolveState().isAdHoc();
            if (visitedComponents.add(component.getResultId())) {
                writeComponent(encoder, component, adhoc);
            }

            writeNode(node, encoder, adhoc);
        });
    }

    private void writeComponent(Encoder encoder, DependencyGraphComponent component, boolean adhoc) throws Exception {
        encoder.writeByte(COMPONENT);
        encoder.writeSmallLong(component.getResultId());
        reasonSerializer.write(encoder, component.getSelectionReason());
        encoder.writeNullableString(component.getRepositoryName());

        encoder.writeBoolean(adhoc);
        if (adhoc) {
            ComponentGraphResolveState componentState = component.getResolveState();
            componentIdSerializer.write(encoder, componentState.getId());
            moduleVersionIdSerializer.write(encoder, componentState.getMetadata().getModuleVersionId());
        } else {
            long instanceId = graphElementStore.storeComponentReference(component.getResolveState());
            encoder.writeSmallLong(instanceId);
        }

        if (allSelectableVariantResults != null) {
            allSelectableVariantResults.add(getAllSelectableVariantResults(component.getResolveState()));
        }
    }

    private  List<ResolvedVariantResult> getAllSelectableVariantResults(ComponentGraphResolveState component) {
        return component.getCandidatesForGraphVariantSelection()
            .getVariantsForAttributeMatching()
            .stream()
            .flatMap(variant -> variant.prepareForArtifactResolution().getArtifactVariants().stream())
            .map(artifactSet -> new DefaultResolvedVariantResult(
                component.getId(),
                Describables.of(artifactSet.getName()),
                attributeDesugaring.desugar(artifactSet.getAttributes().asImmutable()),
                capabilitiesFor(artifactSet.getCapabilities(), component),
                null
            ))
            .collect(Collectors.toList());
    }

    // TODO: Would probably be better if a node already knew its real capabilities instead of
    // correcting for them at the API surface.
    private static ImmutableList<? extends Capability> capabilitiesFor(ImmutableCapabilities capabilities, ComponentGraphResolveState component) {
        if (!capabilities.asSet().isEmpty()) {
            return capabilities.asSet().asList();
        }

        return ImmutableList.of(DefaultImmutableCapability.defaultCapabilityForComponent(component.getMetadata().getModuleVersionId()));
    }

    private void writeNode(DependencyGraphNode node, Encoder encoder, boolean adhoc) throws Exception {
        DependencyGraphComponent component = node.getOwner();

        encoder.writeByte(NODE);
        encoder.writeSmallLong(node.getNodeId());
        encoder.writeSmallLong(component.getResultId());

        encoder.writeBoolean(adhoc);
        if (adhoc) {
            encoder.writeString(node.getMetadata().getName());
            attributeContainerSerializer.write(encoder, node.getMetadata().getAttributes());
            capabilitySerializer.write(encoder, node.getMetadata().getCapabilities());
        } else {
            long instanceId = graphElementStore.storeVariantReference(node.getResolveState());
            encoder.writeSmallLong(instanceId);

            ResolvedGraphVariant externalVariant = node.getExternalVariant();
            if (externalVariant != null) {
                encoder.writeBoolean(true);
                encoder.writeSmallLong(externalVariant.getNodeId());
            } else {
                encoder.writeBoolean(false);
            }
        }

        encoder.writeSmallInt(node.getOutgoingEdges().size());
        for (DependencyGraphEdge dependency : node.getOutgoingEdges()) {
            writeEdge(encoder, dependency);
        }
    }

    private void writeEdge(Encoder encoder, DependencyGraphEdge edge) throws Exception {
        ModuleVersionResolveException failure = edge.getFailure();
        boolean constraint = edge.isConstraint();
        if (failure == null) {
            List<? extends DependencyGraphNode> targetNodes = edge.getTargetNodes();
            if (targetNodes.isEmpty()) {
                throw new IllegalStateException("Edge " + edge + " has no target nodes.");
            }
            if (constraint) {
                writeConstraintEdge(encoder, edge);
            } else {
                writeHardEdge(encoder, edge);
            }
        } else {
            encoder.writeSmallInt(-1);
            encoder.writeBoolean(constraint);
            componentSelectorSerializer.write(encoder, edge.getRequested());
            reasonSerializer.write(encoder, edge.getReason());
            failures.add(failure);
        }
    }

    private void writeConstraintEdge(Encoder encoder, DependencyGraphEdge edge) throws Exception {
        // Only write the first target node for constraints, as this is historical
        // behavior. Eventually, we should model constraints differently in the public
        // API so they do not report a target node at all, as constraints conceptually
        // only target components.
        DependencyGraphNode firstTargetNode = edge.getTargetNodes().get(0);
        if (!mayHaveVirtualPlatforms || !firstTargetNode.getComponent().getModule().isVirtualPlatform()) {
            encoder.writeSmallInt(1);
            encoder.writeBoolean(true);
            componentSelectorSerializer.write(encoder, edge.getRequested());
            encoder.writeSmallLong(firstTargetNode.getNodeId());
        } else {
            encoder.writeSmallInt(0);
        }
    }

    private void writeHardEdge(Encoder encoder, DependencyGraphEdge edge) throws Exception {
        List<? extends DependencyGraphNode> targetNodes = edge.getTargetNodes();

        int size = 0;
        if (mayHaveVirtualPlatforms) {
            for (DependencyGraphNode targetNode : targetNodes) {
                if (!targetNode.getComponent().getModule().isVirtualPlatform()) {
                    size++;
                }
            }
        } else {
            size = targetNodes.size();
        }
        encoder.writeSmallInt(size);
        if (size > 0) {
            encoder.writeBoolean(false);
            componentSelectorSerializer.write(encoder, edge.getRequested());
            for (DependencyGraphNode targetNode : targetNodes) {
                if (!mayHaveVirtualPlatforms || !targetNode.getComponent().getModule().isVirtualPlatform()) {
                    encoder.writeSmallLong(targetNode.getNodeId());
                }
            }
        }
    }

    private record StatefulSerializers(
        AttributeContainerSerializer attributeContainerSerializer,
        Serializer<ComponentSelector> componentSelectorSerializer
    ) {

        public static StatefulSerializers create(
            CapabilitySelectorSerializer capabilitySelectorSerializer,
            AttributesFactory attributesFactory,
            NamedObjectInstantiator namedObjectInstantiator
        ) {
            AttributeContainerSerializer deduplicatingAttributeContainerSerializer = new DeduplicatingAttributeContainerSerializer(
                new DesugaringAttributeContainerSerializer(
                    attributesFactory,
                    namedObjectInstantiator
                )
            );
            DeduplicatingComponentSelectorSerializer deduplicatingComponentSelectorSerializer = new DeduplicatingComponentSelectorSerializer(
                new ComponentSelectorSerializer(
                    deduplicatingAttributeContainerSerializer,
                    capabilitySelectorSerializer
                )
            );

            return new StatefulSerializers(
                deduplicatingAttributeContainerSerializer,
                deduplicatingComponentSelectorSerializer
            );
        }

    }

    private static class GraphFactory {

        private final Object lock = new Object();
        private final BinaryStore.BinaryData data;
        private final Store<GraphStructure> cache;
        private final List<ModuleVersionResolveException> failures;
        private final Set<UnresolvedDependency> dependencyLockingFailures;
        private final Supplier<GraphDeserializer> deserializerFactory;

        GraphFactory(
            BinaryStore.BinaryData data,
            Store<GraphStructure> cache,
            List<ModuleVersionResolveException> failures,
            Set<UnresolvedDependency> dependencyLockingFailures,
            Supplier<GraphDeserializer> deserializerFactory
        ) {
            this.data = data;
            this.failures = failures;
            this.cache = cache;
            this.dependencyLockingFailures = dependencyLockingFailures;
            this.deserializerFactory = deserializerFactory;
        }

        public GraphStructure create() {
            synchronized (lock) {
                return cache.load(() -> {
                    try (BinaryStore.BinaryData reader = data) {
                        return reader.read(decoder ->
                            deserializerFactory.get().deserializeFrom(decoder, failures, dependencyLockingFailures)
                        );
                    } catch (IOException e) {
                        throw throwAsUncheckedException(e);
                    }
                });
            }
        }

    }

    private static class GraphDeserializer {

        private static final DefaultComponentSelectionDescriptor DEPENDENCY_LOCKING =
            new DefaultComponentSelectionDescriptor(ComponentSelectionCause.CONSTRAINT, Describables.of("Dependency locking"));

        private final Serializer<ComponentSelector> componentSelectorSerializer;
        private final AttributeContainerSerializer attributeContainerSerializer;
        private final ThisBuildTreeOnlyGraphElementStore graphElementStore;
        private final ComponentSelectionReasonSerializer reasonSerializer;
        private final Serializer<ComponentIdentifier> componentIdSerializer;
        private final ImmutableCapabilitiesSerializer capabilitySerializer;
        private final Serializer<ModuleVersionIdentifier> moduleVersionIdSerializer;
        private final AttributeDesugaring attributeDesugaring;

        private final GraphStructureBuilder builder = new GraphStructureBuilder();
        private long rootNodeId;
        private int failureIndex = 0;

        public GraphDeserializer(
            Serializer<ComponentSelector> componentSelectorSerializer,
            AttributeContainerSerializer attributeContainerSerializer,
            ThisBuildTreeOnlyGraphElementStore graphElementStore,
            ComponentSelectionReasonSerializer reasonSerializer,
            Serializer<ComponentIdentifier> componentIdSerializer,
            ImmutableCapabilitiesSerializer capabilitySerializer,
            Serializer<ModuleVersionIdentifier> moduleVersionIdSerializer,
            AttributeDesugaring attributeDesugaring
        ) {
            this.componentSelectorSerializer = componentSelectorSerializer;
            this.attributeContainerSerializer = attributeContainerSerializer;
            this.graphElementStore = graphElementStore;
            this.reasonSerializer = reasonSerializer;
            this.componentIdSerializer = componentIdSerializer;
            this.capabilitySerializer = capabilitySerializer;
            this.moduleVersionIdSerializer = moduleVersionIdSerializer;
            this.attributeDesugaring = attributeDesugaring;
        }

        public GraphStructure deserializeFrom(Decoder decoder, List<ModuleVersionResolveException> edgeFailures, Set<UnresolvedDependency> dependencyLockingFailures) {
            int valuesRead = 0;
            byte type = -1;
            Timer clock = Time.startTimer();
            try {
                this.rootNodeId = decoder.readSmallLong();
                builder.start(rootNodeId);

                while (true) {
                    type = decoder.readByte();
                    valuesRead++;
                    switch (type) {
                        case COMPONENT -> readComponent(decoder);
                        case NODE -> readNode(decoder, edgeFailures, dependencyLockingFailures);
                        case END -> {
                            return builder.build();
                        }
                        default -> throw new IOException("Unknown value type read from stream: " + type);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Problems loading the resolution results (" + clock.getElapsed() + "). "
                    + "Read " + valuesRead + " values, last was: " + type, e);
            }
        }

        private void readComponent(Decoder decoder) throws Exception {
            long id = decoder.readSmallLong();
            ComponentSelectionReasonInternal selectionReason = reasonSerializer.read(decoder);
            String repositoryName = decoder.readNullableString();

            ComponentIdentifier componentIdentifier;
            ModuleVersionIdentifier moduleVersionId;
            if (decoder.readBoolean()) {
                componentIdentifier = componentIdSerializer.read(decoder);
                moduleVersionId = moduleVersionIdSerializer.read(decoder);
            } else {
                long instanceId = decoder.readSmallLong();
                ComponentGraphResolveState component = graphElementStore.getComponent(instanceId);
                componentIdentifier = component.getId();
                moduleVersionId = component.getMetadata().getModuleVersionId();
            }

            builder.addComponent(
                id,
                selectionReason,
                repositoryName,
                componentIdentifier,
                moduleVersionId
            );
        }

        private void readNode(
            Decoder decoder,
            List<ModuleVersionResolveException> edgeFailures,
            Set<UnresolvedDependency> dependencyLockingFailures
        ) throws Exception {
            long nodeId = decoder.readSmallLong();
            long ownerId = decoder.readSmallLong();

            String variantName;
            ImmutableAttributes attributes;
            ImmutableCapabilities rawCapabilities;
            long externalVariantId = -1;
            if (decoder.readBoolean()) {
                variantName = decoder.readString();
                attributes = attributeContainerSerializer.read(decoder);
                rawCapabilities = capabilitySerializer.read(decoder);
            } else {
                long instanceId = decoder.readSmallLong();
                VariantGraphResolveState variant = graphElementStore.getVariant(instanceId);
                variantName = variant.getMetadata().getName();
                attributes = attributeDesugaring.desugar(variant.getMetadata().getAttributes());
                rawCapabilities = variant.getMetadata().getCapabilities();

                if (decoder.readBoolean()) {
                    externalVariantId = decoder.readSmallLong();
                }
            }

            builder.addNode(
                nodeId,
                ownerId,
                attributes,
                rawCapabilities,
                variantName,
                externalVariantId
            );

            readEdges(decoder, edgeFailures);

            // TODO: These failures should be injected way earlier while validating the graph
            // rather than side-loading them when building the graph structure representation.
            if (nodeId == rootNodeId) {
                for (UnresolvedDependency failure : dependencyLockingFailures) {
                    ModuleVersionSelector failureSelector = failure.getSelector();
                    ModuleComponentSelector failureComponentSelector = DefaultModuleComponentSelector.newSelector(failureSelector.getModule(), failureSelector.getVersion());
                    builder.addFailedEdge(
                        failureComponentSelector,
                        true,
                        ComponentSelectionReasons.of(DEPENDENCY_LOCKING),
                        new ModuleVersionResolveException(failureComponentSelector, () -> "Dependency lock state out of date", failure.getProblem())
                    );
                }
            }
        }

        private void readEdges(Decoder decoder, List<ModuleVersionResolveException> edgeFailures) throws Exception {
            int edges = decoder.readSmallInt();
            for (int i = 0; i < edges; i++) {
                int targetCount = decoder.readSmallInt();
                if (targetCount == -1) {
                    boolean constraint = decoder.readBoolean();
                    ComponentSelector selector = componentSelectorSerializer.read(decoder);
                    ComponentSelectionReasonInternal reason = reasonSerializer.read(decoder);
                    builder.addFailedEdge(
                        selector,
                        constraint,
                        reason,
                        edgeFailures.get(failureIndex++)
                    );
                } else if (targetCount != 0) {
                    boolean constraint = decoder.readBoolean();
                    ComponentSelector selector = componentSelectorSerializer.read(decoder);
                    for (int j = 0; j < targetCount; j++) {
                        long targetNodeId = decoder.readSmallLong();
                        builder.addSuccessfulEdge(
                            selector,
                            constraint,
                            targetNodeId
                        );
                    }
                }
            }
        }

    }

}
