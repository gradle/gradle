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
import org.gradle.api.file.FileCollection;
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public class DefaultArtifactGraph implements ArtifactGraphInternal {

    private final Lazy<Details> lazyDetails;
    private final boolean lenient;
    private final ArtifactCollectionFactory artifactCollectionFactory;

    private final Int2ObjectMap<ArtifactNode> nodeCache = new Int2ObjectOpenHashMap<>();

    public DefaultArtifactGraph(
        Supplier<DefaultArtifactGraph.Details> detailsProvider,
        boolean lenient,
        ArtifactCollectionFactory artifactCollectionFactory
    ) {
        this.lazyDetails = Lazy.unsafe().of(detailsProvider);
        this.lenient = lenient;
        this.artifactCollectionFactory = artifactCollectionFactory;
    }

    @Override
    public Provider<ArtifactNode> getRoot() {
        return new DefaultProvider<>(() -> getNode(lazyDetails.get().graphStructure().nodes().root()));
    }

    @Override
    public ArtifactCollection getArtifacts(Spec<ArtifactNode> filter) {
        return createLazyArtifactCollection(lazyDetails.map(details -> {
            int count = details.graphStructure().nodes().count();
            IntStream indices = IntStream.range(0, count)
                .filter(index -> filter.isSatisfiedBy(getNode(index)));
            return artifactCollectionDetailsForIndices(details, indices);
        }));
    }

    @Override
    public FileCollection getFiles(Spec<ArtifactNode> filter) {
        return getArtifacts(filter).getArtifactFiles();
    }

    @Override
    public ArtifactCollection getArtifactsFromRoot(Function<ArtifactNode, Iterable<ArtifactNode>> selector) {
        return createLazyArtifactCollection(lazyDetails.map(details -> {
            IntStream indices = Streams.stream(selector.apply(getRoot().get()))
                .mapToInt(node -> ((DefaultArtifactNode) node).getNodeIndex());
            return artifactCollectionDetailsForIndices(details, indices);
        }));
    }

    private ArtifactCollection createLazyArtifactCollection(Lazy<ArtifactCollectionDetails> detailsProvider) {
        return artifactCollectionFactory.create(
            new ArtifactCollectionFactory.LazyResolvedArtifactSet(detailsProvider.map(ArtifactCollectionDetails::artifactSet)),
            new ArtifactCollectionFactory.LazyResolutionFailureProvider(detailsProvider.map(ArtifactCollectionDetails::failures)),
            lenient
        );
    }

    private static ArtifactCollectionDetails artifactCollectionDetailsForIndices(Details details, IntStream nodeIndices) {
        GraphStructure structure = details.graphStructure();
        GraphStructure.Nodes nodes = structure.nodes();
        GraphStructure.Edges edges = structure.edges();
        int count = nodes.count();

        List<ResolvedArtifactSet> includedArtifactSets = new ArrayList<>(count);
        List<GraphStructure.Edges.EdgeFailure> failures = new ArrayList<>();
        nodeIndices.forEach(index -> {
            includedArtifactSets.add(details.artifacts.getArtifactsWithId(index));
            visitFailureForNode(index, edges, failures::add);
        });

        return new ArtifactCollectionDetails(
            CompositeResolvedArtifactSet.of(includedArtifactSets),
            failures.isEmpty() ? ResolutionFailureProvider.EMPTY : new EdgeFailuresBackedFailureProvider(failures)
        );
    }

    private static void visitFailureForNode(int index, GraphStructure.Edges edges, Consumer<GraphStructure.Edges.EdgeFailure> visitor) {
        for (int e = edges.start(index); e < edges.end(index); e++) {
            int targetNode = edges.targetNode(e);
            if (targetNode == -1) {
                visitor.accept(edges.failure(e));
            }
        }
    }

    @Override
    public FileCollection getFilesFromRoot(Function<ArtifactNode, Iterable<ArtifactNode>> selector) {
        return getArtifactsFromRoot(selector).getArtifactFiles();
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

        List<GraphStructure.Edges.EdgeFailure> failures = new ArrayList<>();
        visitFailureForNode(nodeIndex, details.graphStructure().edges(), failures::add);

        return artifactCollectionFactory.create(
            artifacts,
            failures.isEmpty() ? ResolutionFailureProvider.EMPTY : new EdgeFailuresBackedFailureProvider(failures),
            lenient
        );
    }

    public record Details(
        GraphStructure graphStructure,
        SelectedArtifactResults artifacts
    ) {}

    private record EdgeFailuresBackedFailureProvider(
        List<GraphStructure.Edges.EdgeFailure> failures
    ) implements ResolutionFailureProvider {

        @Override
        public boolean hasAnyFailure() {
            return !failures.isEmpty();
        }

        @Override
        public void visitFailures(Consumer<Throwable> visitor) {
            if (!failures.isEmpty()) {
                for (GraphStructure.Edges.EdgeFailure failure : failures) {
                    visitor.accept(failure.failure());
                }
            }
        }

    }

    record ArtifactCollectionDetails(
        ResolvedArtifactSet artifactSet,
        ResolutionFailureProvider failures
    ) { }

}
