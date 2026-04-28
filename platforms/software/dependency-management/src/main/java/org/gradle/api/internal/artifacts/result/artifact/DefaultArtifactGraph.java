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

package org.gradle.api.internal.artifacts.result.artifact;

import com.google.common.collect.Streams;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.internal.artifacts.ivyservice.ResolutionFailureProvider;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.CompositeResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedArtifactResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.GraphStructure;
import org.gradle.api.internal.artifacts.resolver.ArtifactCollectionFactory;
import org.gradle.api.internal.provider.DefaultProvider;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.internal.lazy.Lazy;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public class DefaultArtifactGraph implements ArtifactGraphInternal {

    private final Lazy<Details> lazyDetails;
    private final boolean lenient;
    private final ArtifactCollectionFactory artifactCollectionFactory;

    private final Int2ObjectMap<ArtifactNode> nodeCache = new Int2ObjectOpenHashMap<>();

    public DefaultArtifactGraph(
        Lazy<Details> lazyDetails,
        boolean lenient,
        ArtifactCollectionFactory artifactCollectionFactory
    ) {
        this.lazyDetails = lazyDetails;
        this.lenient = lenient;
        this.artifactCollectionFactory = artifactCollectionFactory;
    }

    @Override
    public Provider<ArtifactNode> getRoot() {
        return new DefaultProvider<>(() -> getNode(lazyDetails.get().graphStructure().nodes().root()));
    }

    @Override
    public ArtifactCollection getArtifacts(Spec<ArtifactNode> filter) {
        return createLazyArtifactCollection(() -> {
            int count = lazyDetails.get().graphStructure().nodes().count();
            return IntStream.range(0, count)
                .filter(index -> filter.isSatisfiedBy(getNode(index)));
        });
    }

    @Override
    public ArtifactCollection getArtifactsFromTraversal(Function<ArtifactNode, Iterable<ArtifactNode>> selector) {
        return createLazyArtifactCollection(() ->
            Streams.stream(selector.apply(getRoot().get())).mapToInt(node -> {
                int index = ((DefaultArtifactNode) node).getNodeIndex();
                if (nodeCache.get(index) == nodeCache.defaultReturnValue()) {
                    throw new IllegalArgumentException("Cannot select a node that is not in the graph: " + node);
                }
                return index;
            })
        );
    }

    private ArtifactCollection createLazyArtifactCollection(Supplier<IntStream> indices) {
        ResolvedArtifactSet artifactSet = new ArtifactCollectionFactory.LazyResolvedArtifactSet(lazyDetails.map(details -> {
            SelectedArtifactResults artifacts = details.artifacts;
            List<ResolvedArtifactSet> includedArtifactSets =
                indices.get().mapToObj(artifacts::getArtifactsWithId).toList();
            return CompositeResolvedArtifactSet.of(includedArtifactSets);
        }));

        return artifactCollectionFactory.create(
            artifactSet,
            ResolutionFailureProvider.EMPTY,
            lenient
        );
    }

    @Override
    public ArtifactNode getNode(int id) {
        return nodeCache.computeIfAbsent(id, (int nodeIndex) -> {
            GraphStructure graphStructure = lazyDetails.get().graphStructure();
            return new DefaultArtifactNode(
                nodeIndex,
                this,
                graphStructure
            );
        });
    }

    @Override
    public ArtifactCollection artifactsFor(int nodeIndex) {
        Details details = lazyDetails.get();
        var artifacts = details.artifacts.getArtifactsWithId(nodeIndex);
        return artifactCollectionFactory.create(
            artifacts,
            ResolutionFailureProvider.EMPTY,
            lenient
        );
    }

    public record Details(
        GraphStructure graphStructure,
        SelectedArtifactResults artifacts
    ) {}

}
