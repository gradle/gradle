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

import com.google.common.collect.Lists;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphComponent;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphDependency;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.RootGraphNode;
import org.gradle.api.internal.artifacts.result.MinimalResolutionResult;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.internal.BinaryStore;
import org.gradle.cache.internal.Store;
import org.gradle.internal.Factory;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.gradle.internal.UncheckedException.throwAsUncheckedException;

public class StreamingResolutionResultBuilder implements DependencyGraphVisitor {
    private final static byte ROOT = 1;
    private final static byte COMPONENT = 2;
    private final static byte SELECTOR = 4;
    private final static byte DEPENDENCY = 5;

    private final Map<ComponentSelector, ModuleVersionResolveException> failures = new HashMap<>();
    private final BinaryStore store;
    private final ComponentResultSerializer componentResultSerializer;
    private final Store<ResolvedComponentResult> cache;
    private final ComponentSelectorSerializer componentSelectorSerializer;
    private final DependencyResultSerializer dependencyResultSerializer;
    private final Set<Long> visitedComponents = new HashSet<>();

    private ImmutableAttributes rootAttributes;
    private boolean mayHaveVirtualPlatforms;

    public StreamingResolutionResultBuilder(
        BinaryStore store,
        Store<ResolvedComponentResult> cache,
        AttributeContainerSerializer attributeContainerSerializer,
        ComponentDetailsSerializer componentDetailsSerializer,
        SelectedVariantSerializer selectedVariantSerializer,
        ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory,
        boolean includeAllSelectableVariantResults
    ) {
        this.dependencyResultSerializer = new DependencyResultSerializer(componentSelectionDescriptorFactory);
        this.componentResultSerializer = new ComponentResultSerializer(componentDetailsSerializer, selectedVariantSerializer, componentSelectionDescriptorFactory, includeAllSelectableVariantResults);
        this.store = store;
        this.cache = cache;
        this.componentSelectorSerializer = new ComponentSelectorSerializer(attributeContainerSerializer);
    }

    public MinimalResolutionResult getResolutionResult(Set<UnresolvedDependency> dependencyLockingFailures) {
        BinaryStore.BinaryData data = store.done();
        RootFactory rootSource = new RootFactory(data, failures, cache, componentSelectorSerializer, dependencyResultSerializer, componentResultSerializer, dependencyLockingFailures);
        return new MinimalResolutionResult(rootSource::create, rootAttributes);
    }

    @Override
    public void start(final RootGraphNode root) {
        rootAttributes = root.getMetadata().getAttributes();
        mayHaveVirtualPlatforms = root.getResolveOptimizations().mayHaveVirtualPlatforms();
    }

    @Override
    public void finish(final RootGraphNode root) {
        store.write(encoder -> {
            encoder.writeByte(ROOT);
            encoder.writeSmallLong(root.getOwner().getResultId());
        });
    }

    @Override
    public void visitNode(DependencyGraphNode node) {
        final DependencyGraphComponent component = node.getOwner();
        if (visitedComponents.add(component.getResultId())) {
            store.write(encoder -> {
                encoder.writeByte(COMPONENT);
                componentResultSerializer.write(encoder, component);
            });
        }
    }

    @Override
    public void visitSelector(final DependencyGraphSelector selector) {
        store.write(encoder -> {
            encoder.writeByte(SELECTOR);
            encoder.writeSmallLong(selector.getResultId());
            componentSelectorSerializer.write(encoder, selector.getRequested());
        });
    }

    @Override
    public void visitEdges(DependencyGraphNode node) {
        final Long fromComponent = node.getOwner().getResultId();
        final Collection<? extends DependencyGraphEdge> dependencies = mayHaveVirtualPlatforms
            ? node.getOutgoingEdges().stream()
            .filter(dep -> !dep.isTargetVirtualPlatform())
            .collect(Collectors.toList())
            : node.getOutgoingEdges();
        if (!dependencies.isEmpty()) {
            store.write(encoder -> {
                encoder.writeByte(DEPENDENCY);
                encoder.writeSmallLong(fromComponent);
                encoder.writeSmallInt(dependencies.size());
                for (DependencyGraphEdge dependency : dependencies) {
                    dependencyResultSerializer.write(encoder, dependency);
                    if (dependency.getFailure() != null) {
                        //by keying the failures only by 'requested' we lose some precision
                        //at edge case we'll lose info about a different exception if we have different failure for the same requested version
                        failures.put(dependency.getRequested(), dependency.getFailure());
                    }
                }
            });
        }
    }

    private static class RootFactory implements Factory<ResolvedComponentResult> {

        private final static Logger LOG = Logging.getLogger(RootFactory.class);
        private final ComponentResultSerializer componentResultSerializer;

        private final BinaryStore.BinaryData data;
        private final Map<ComponentSelector, ModuleVersionResolveException> failures;
        private final Store<ResolvedComponentResult> cache;
        private final Object lock = new Object();
        private final ComponentSelectorSerializer componentSelectorSerializer;
        private final DependencyResultSerializer dependencyResultSerializer;
        private final Set<UnresolvedDependency> dependencyLockingFailures;

        RootFactory(BinaryStore.BinaryData data, Map<ComponentSelector, ModuleVersionResolveException> failures, Store<ResolvedComponentResult> cache, ComponentSelectorSerializer componentSelectorSerializer, DependencyResultSerializer dependencyResultSerializer, ComponentResultSerializer componentResultSerializer, Set<UnresolvedDependency> dependencyLockingFailures) {
            this.data = data;
            this.failures = failures;
            this.cache = cache;
            this.componentResultSerializer = componentResultSerializer;
            this.componentSelectorSerializer = componentSelectorSerializer;
            this.dependencyResultSerializer = dependencyResultSerializer;
            this.dependencyLockingFailures = dependencyLockingFailures;
        }

        @Override
        public ResolvedComponentResult create() {
            synchronized (lock) {
                return cache.load(() -> {
                    try {
                        return data.read(this::deserialize);
                    } finally {
                        try {
                            data.close();
                        } catch (IOException e) {
                            throw throwAsUncheckedException(e);
                        }
                    }
                });
            }
        }

        private ResolvedComponentResult deserialize(Decoder decoder) {
            componentSelectorSerializer.reset();
            int valuesRead = 0;
            byte type = -1;
            Timer clock = Time.startTimer();
            try {
                ResolutionResultGraphBuilder builder = new ResolutionResultGraphBuilder();
                Map<Long, ComponentSelector> selectors = new HashMap<>();
                while (true) {
                    type = decoder.readByte();
                    valuesRead++;
                    switch (type) {
                        case ROOT:
                            // Last entry, complete the result
                            Long rootId = decoder.readSmallLong();
                            builder.addDependencyLockingFailures(rootId, dependencyLockingFailures);
                            ResolvedComponentResult root = builder.getRoot(rootId);
                            LOG.debug("Loaded resolution results ({}) from {}", clock.getElapsed(), data);
                            return root;
                        case COMPONENT:
                            componentResultSerializer.readInto(decoder, builder);
                            break;
                        case SELECTOR:
                            Long id = decoder.readSmallLong();
                            ComponentSelector selector = componentSelectorSerializer.read(decoder);
                            selectors.put(id, selector);
                            break;
                        case DEPENDENCY:
                            Long fromId = decoder.readSmallLong();
                            int size = decoder.readSmallInt();
                            if (size > 0) {
                                List<ResolvedGraphDependency> deps = Lists.newArrayListWithExpectedSize(size);
                                for (int i = 0; i < size; i++) {
                                    deps.add(dependencyResultSerializer.read(decoder, selectors, failures));
                                }
                                builder.visitOutgoingEdges(fromId, deps);
                            }
                            break;
                        default:
                            throw new IOException("Unknown value type read from stream: " + type);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Problems loading the resolution results (" + clock.getElapsed() + "). "
                    + "Read " + valuesRead + " values, last was: " + type, e);
            }
        }
    }
}
