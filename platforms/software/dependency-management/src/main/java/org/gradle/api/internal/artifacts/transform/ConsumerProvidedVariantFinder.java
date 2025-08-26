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

package org.gradle.api.internal.artifacts.transform;

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.artifacts.TransformRegistration;
import org.gradle.api.internal.artifacts.VariantTransformRegistry;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.attributes.AttributeSchemaServices;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.api.internal.attributes.matching.AttributeMatcher;
import org.gradle.internal.lazy.Lazy;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Finds all artifact sets that can be created from a given set of producer artifact
 * sets using the consumer's artifact transforms. Transforms can be chained. If multiple
 * chains can lead to the same outcome, the shortest paths are selected.
 *
 * Caches the results, as often the same request is made for many components in a
 * dependency graph.
 */
@ServiceScope(Scope.Project.class)
public class ConsumerProvidedVariantFinder {

    private final VariantTransformRegistry registeredTransforms;
    private final AttributesFactory attributesFactory;
    private final Lazy<AttributeMatcher> matcher;
    private final TransformCache transformCache;

    @Inject
    public ConsumerProvidedVariantFinder(
        VariantTransformRegistry registeredTransforms,
        AttributesSchemaInternal schema,
        AttributesFactory attributesFactory,
        AttributeSchemaServices attributeSchemaServices
    ) {
        this.registeredTransforms = registeredTransforms;
        this.attributesFactory = attributesFactory;
        this.matcher = Lazy.locking().of(() -> {
            // TODO: This is incorrect. We fail to merge the consumer schema with the producer schema
            // and therefore we miss producer rules when matching transforms.
            // Instead, this class should be refactored to accept a matcher as a parameter,
            // where the matcher has already been created with the consumer and producer schema.
            ImmutableAttributesSchema immutable = attributeSchemaServices.getSchemaFactory().create(schema);
            return attributeSchemaServices.getMatcher(immutable, ImmutableAttributesSchema.EMPTY);
        });
        this.transformCache = new TransformCache(this::findShortestTransformChains);
    }

    /**
     * Executes the transform chain detection algorithm given a set of producer artifact sets and the requested
     * attributes. Only the transform chains of the shortest depth are returned, and all results are
     * guaranteed to have the same depth.
     *
     * @param sources The set of producer artifact sets.
     * @param requested The requested attributes.
     *
     * @return A collection of transform chains which, if applied to the corresponding source artifact set,
     * will produce an artifact set compatible with the requested attributes.
     */
    public List<TransformedVariant> findCandidateTransformationChains(List<ResolvedVariant> sources, ImmutableAttributes requested) {
        return transformCache.query(sources, requested);
    }

    /**
     * An edge in the {@link TransformStateGraph}.
     */
    private static class TransformStateEdge {

        /**
         * The source attribute state of this edge.
         */
        final ImmutableAttributes from;

        /**
         * The index of the transform that produces the target attribute state
         * of this edge from the source attribute state.
         */
        final int transformIndex;

        public TransformStateEdge(ImmutableAttributes from, int transformIndex) {
            this.from = from;
            this.transformIndex = transformIndex;
        }

    }

    /**
     * Details about a given state in the {@link TransformStateGraph}.
     */
    private static class StateDetails {

        /**
         * The length of the shortest path to this state from the initial state.
         */
        final int depth;

        /**
         * All known edges leading to this state.
         */
        final ImmutableLinkedList<TransformStateEdge> incomingEdges;

        public StateDetails(
            int depth,
            ImmutableLinkedList<TransformStateEdge> incomingEdges
        ) {
            this.depth = depth;
            this.incomingEdges = incomingEdges;
        }

    }

    /**
     * A direct graph of attribute states connected by transform edges which
     * transition from one state to another.
     */
    private static class TransformStateGraph {

        /**
         * All shortest-path states that have not yet been processed.
         */
        private final List<ImmutableAttributes> nextStates = new ArrayList<>();

        /**
         * All states that have been seen so far, along with their details.
         */
        private final Map<ImmutableAttributes, StateDetails> seenStates = new HashMap<>();

        /**
         * Creates a new graph with the provided initial state.
         *
         * @param requestedState The state of the graph to start searching from.
         */
        public TransformStateGraph(ImmutableAttributes requestedState) {
            nextStates.add(requestedState);
            seenStates.put(requestedState, new StateDetails(0, ImmutableLinkedList.of()));
        }

        /**
         * Adds an edge to this graph, between the given states, using the provided transform.
         *
         * @param toState The target state of the edge.
         * @param fromState The source state of the edge.
         * @param transformIndex The index of the transform that produces the target state from the source state.
         */
        public void addEdge(
            ImmutableAttributes toState,
            ImmutableAttributes fromState,
            int transformIndex
        ) {
            StateDetails fromDetails = getDetails(fromState);
            int newDepth = fromDetails.depth + 1;

            seenStates.compute(toState, (key, existing) -> {
                if (existing == null) {
                    // We have never found this attribute state before. Keep searching from it.
                    nextStates.add(toState);
                    return new StateDetails(
                        newDepth,
                        ImmutableLinkedList.of(new TransformStateEdge(fromState, transformIndex))
                    );
                } else {
                    // We have found another transform chain that gets us to the same state.
                    if (newDepth == existing.depth) {
                        // The new path to this state is the same length as the existing path.
                        // Make sure to keep track of all known paths to this state.
                        return new StateDetails(
                            existing.depth,
                            existing.incomingEdges.cons(new TransformStateEdge(fromState, transformIndex))
                        );
                    } else if (newDepth > existing.depth) {
                        // The existing path to this state is shorter than the new path.
                        // Discard the new path since they do not contribute to the shortest paths.
                        return new StateDetails(
                            existing.depth,
                            existing.incomingEdges
                        );
                    } else {
                        // We search the graph BFS, so we never expect to find a new path to a
                        // state that is shorter than an existing path.
                        throw new IllegalStateException("Unexpectedly found a shorter path to an existing transform chain state.");
                    }
                }
            });
        }

        /**
         * Consumes all unprocessed states in this graph, returning them and clearing the internal list.
         */
        public ImmutableList<ImmutableAttributes> consumeNextStates() {
            ImmutableList<ImmutableAttributes> result = ImmutableList.copyOf(nextStates);
            nextStates.clear();
            return result;
        }

        /**
         * Gets the details of a given state in this graph.
         */
        public StateDetails getDetails(ImmutableAttributes state) {
            StateDetails result = seenStates.get(state);
            assert result != null;
            return result;
        }

    }

    /**
     * A cached result of the transform chain detection algorithm. References an index within the candidate source
     * list instead of an actual artifact set itself, so that this result can be cached and used for distinct candidate lists
     * that otherwise share the same attributes.
     */
    private static class CachedTransformChain {

        private final int sourceIndex;
        private final VariantDefinition chain;

        public CachedTransformChain(int sourceIndex, VariantDefinition chain) {
            this.sourceIndex = sourceIndex;
            this.chain = chain;
        }

    }

    /**
     * Finds all shortest-path transform chains from any of the given source attribute
     * sets to the requested attributes. This algorithm is split into a two-phase process.
     * <p>
     * The first phase performs a breadth-first search of the state-space of attribute combinations
     * reachable by applying all registered transforms at any given depth. The search begins at the
     * requested attributes and works backwards to either discover all shortest-path states that
     * satisfy the requested attributes, or to exhaust the state-space.
     * <p>
     * The second phase consumes the discovered shortest-path states, walking the graph to discover
     * all transform chains that may be used to reach the requested attributes from any of the source
     * attribute sets.
     * <p>
     * Splitting the algorithm into two phases allows us to avoid exploring transform _paths_, and
     * instead allows us to transform the _states_ that each path may reach. This allows us to effectively
     * de-duplicate the traversal, converting what would otherwise be an exponential-time algorithm into
     * one proportionate to the number of reachable attribute states.
     */
    private ImmutableList<CachedTransformChain> findShortestTransformChains(List<ImmutableAttributes> sources, ImmutableAttributes requested) {
        AttributeMatcher attributeMatcher = matcher.get();
        ImmutableList<TransformRegistration> transforms = ImmutableList.copyOf(registeredTransforms.getRegistrations());

        TransformStateGraph stateGraph = new TransformStateGraph(requested);

        ImmutableList<ImmutableAttributes> toProcess;
        while (!(toProcess = stateGraph.consumeNextStates()).isEmpty()) {
            List<CachedTransformChain> results = new ArrayList<>();
            // For each candidate state, attempt to find a source attribute set that may sit at the root.
            for (ImmutableAttributes state : toProcess) {
                StateDetails details = stateGraph.getDetails(state);
                for (int i = 0; i < sources.size(); i++) {
                    ImmutableAttributes sourceAttrs = sources.get(i);
                    if (attributeMatcher.isMatchingCandidate(sourceAttrs, state)) {
                        visitSolutionTransformChains(
                            i, sourceAttrs, details, stateGraph, transforms, results::add
                        );
                    }
                }
            }

            // If we have a result at this depth, don't bother building the next depth's states.
            if (!results.isEmpty()) {
                return ImmutableList.copyOf(results);
            }

            // Construct new states for processing at the next depth in case we can't find any solutions at this depth.
            for (ImmutableAttributes state : toProcess) {
                for (int i = 0; i < transforms.size(); i++) {
                    TransformRegistration nextTransform = transforms.get(i);
                    if (attributeMatcher.isMatchingCandidate(nextTransform.getTo(), state)) {
                        ImmutableAttributes newState = attributesFactory.concat(state, nextTransform.getFrom());
                        stateGraph.addEdge(newState, state, i);
                    }
                }
            }
        }

        return ImmutableList.of();
    }

    /**
     * Starting from the given source node with the given attributes, visit all paths
     * through the state graph starting at the {@code pathDetails} state.
     *
     * @param sourceIndex The index of the source node.
     * @param sourceAttrs The attributes of the source node.
     * @param startTransformNode The details of the node in the state graph to start from.
     * @param stateGraph The state graph to traverse.
     * @param transforms The list of registered transforms.
     * @param visitor A visitor to accept each discovered transform chain.
     */
    private void visitSolutionTransformChains(
        int sourceIndex,
        ImmutableAttributes sourceAttrs,
        StateDetails startTransformNode,
        TransformStateGraph stateGraph,
        ImmutableList<TransformRegistration> transforms,
        Consumer<CachedTransformChain> visitor
    ) {
        // We assume a solution has at least one transform, otherwise there is no need to perform transforms.
        assert !startTransformNode.incomingEdges.isEmpty();
        assert startTransformNode.depth != 0;

        // For each transform from the start node, visit all paths that transform the
        // source attributes to the requested attributes.
        for (TransformStateEdge edge : startTransformNode.incomingEdges) {
            TransformRegistration transform = transforms.get(edge.transformIndex);
            DefaultVariantDefinition base = new DefaultVariantDefinition(
                null,
                attributesFactory.concat(sourceAttrs, transform.getTo()),
                transform.getTransformStep()
            );
            ImmutableLinkedList<Integer> usedTransforms = ImmutableLinkedList.of(edge.transformIndex);
            StateDetails nextDetails = stateGraph.getDetails(edge.from);
            visitSolutionTransformChains(base, nextDetails, usedTransforms, stateGraph, transforms, chain -> {
                visitor.accept(new CachedTransformChain(sourceIndex, chain));
            });
        }
    }

    /**
     * Recursively visits all paths through the state graph starting at the given node,
     * appending each path to the given base variant definition.
     *
     * @param base The base variant definition to append to.
     * @param startTransformNode The details of the node in the state graph to start from.
     * @param usedTransforms All transforms already used in this path.
     * @param stateGraph The state graph to traverse.
     * @param transforms The list of registered transforms.
     * @param visitor A visitor to accept each discovered transform chain.
     */
    private void visitSolutionTransformChains(
        DefaultVariantDefinition base,
        StateDetails startTransformNode,
        ImmutableLinkedList<Integer> usedTransforms,
        TransformStateGraph stateGraph,
        ImmutableList<TransformRegistration> transforms,
        Consumer<DefaultVariantDefinition> visitor
    ) {
        if (startTransformNode.incomingEdges.isEmpty()) {
            // We've reached the requested attributes.
            assert startTransformNode.depth == 0;
            visitor.accept(base);
        } else {
            // We have more transforms to apply.
            assert startTransformNode.depth > 0;
            for (TransformStateEdge stateChain : startTransformNode.incomingEdges) {
                if (usedTransforms.contains(stateChain.transformIndex)) {
                    // Don't allow cycles or reuse of transforms in the transform chain.
                    continue;
                }

                TransformRegistration transform = transforms.get(stateChain.transformIndex);
                DefaultVariantDefinition last = new DefaultVariantDefinition(
                    base,
                    attributesFactory.concat(base.getTargetAttributes(), transform.getTo()),
                    transform.getTransformStep()
                );
                StateDetails nextDetails = stateGraph.getDetails(stateChain.from);
                visitSolutionTransformChains(last, nextDetails, usedTransforms.cons(stateChain.transformIndex), stateGraph, transforms, visitor);
            }
        }
    }

    /**
     * Caches calls to the transform chain selection algorithm. The cached results are stored independent
     * to the source artifacts sets, so that only the attributes of the input artifact sets are cached.
     * This way, if multiple calls are made with different candidate artifact sets but those candidates have
     * the same attributes, the cached results may be used.
     */
    private static class TransformCache {

        private final ConcurrentHashMap<CacheKey, List<CachedTransformChain>> cache = new ConcurrentHashMap<>();
        private final BiFunction<List<ImmutableAttributes>, ImmutableAttributes, List<CachedTransformChain>> action;

        public TransformCache(BiFunction<List<ImmutableAttributes>, ImmutableAttributes, List<CachedTransformChain>> action) {
            this.action = action;
        }

        private List<TransformedVariant> query(
            List<ResolvedVariant> sources, ImmutableAttributes requested
        ) {
            List<ImmutableAttributes> variantAttributes = new ArrayList<>(sources.size());
            for (ResolvedVariant variant : sources) {
                variantAttributes.add(variant.getAttributes());
            }
            List<CachedTransformChain> cached = cache.computeIfAbsent(new CacheKey(variantAttributes, requested), key -> action.apply(key.variantAttributes, key.requested));
            List<TransformedVariant> output = new ArrayList<>(cached.size());
            for (CachedTransformChain variant : cached) {
                output.add(new TransformedVariant(sources.get(variant.sourceIndex), variant.chain));
            }
            return output;
        }

        private static class CacheKey {

            private final List<ImmutableAttributes> variantAttributes;
            private final ImmutableAttributes requested;
            private final int hashCode;

            public CacheKey(List<ImmutableAttributes> variantAttributes, ImmutableAttributes requested) {
                this.variantAttributes = variantAttributes;
                this.requested = requested;
                this.hashCode = 31 * variantAttributes.hashCode() + requested.hashCode();
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }
                CacheKey cacheKey = (CacheKey) o;
                return variantAttributes.equals(cacheKey.variantAttributes) && requested.equals(cacheKey.requested);
            }

            @Override
            public int hashCode() {
                return hashCode;
            }

        }

    }

    /**
     * A simple, immutable, null-hostile, linked list.
     * <p>
     * This list attempts to avoid object allocation when appending
     * lists by reusing the existing list as the tail of the new list.
     *
     * @param <T> The type of element held in the list.
     */
    @NullMarked
    public static final class ImmutableLinkedList<T> implements Iterable<T> {

        private final @Nullable T head;
        private final @Nullable ImmutableLinkedList<T> tail;

        /**
         * An empty list.
         */
        public static <T> ImmutableLinkedList<T> of() {
            return new ImmutableLinkedList<>(null, null);
        }

        /**
         * Create a list with a single element.
         */
        public static <T> ImmutableLinkedList<T> of(T value) {
            return new ImmutableLinkedList<>(value, null);
        }

        private ImmutableLinkedList(@Nullable T head, @Nullable ImmutableLinkedList<T> tail) {
            this.head = head;
            this.tail = tail;
        }

        /**
         * Creates a new list by adding the provided value.
         *
         * @param newHead The element to add.
         *
         * @return A new list containing the provided element.
         */
        public ImmutableLinkedList<T> cons(T newHead) {
            return new ImmutableLinkedList<>(newHead, this);
        }

        /**
         * Checks if the list contains a specific element.
         *
         * @param value The element to search for.
         * @return true if the element is found, otherwise false.
         */
        public boolean contains(T value) {
            return value.equals(head) || (tail != null && tail.contains(value));
        }

        /**
         * Determines if the list is empty.
         */
        public boolean isEmpty() {
            return head == null;
        }

        /**
         * Returns an iterator to traverse the list's elements.
         */
        @Override
        public Iterator<T> iterator() {
            return new Iterator<T>() {
                private @Nullable ImmutableLinkedList<T> current = ImmutableLinkedList.this;

                @Override
                public boolean hasNext() {
                    return current != null && current.head != null;
                }

                @Override
                public T next() {
                    if (current == null || current.head == null) {
                        throw new NoSuchElementException("No more elements in the list.");
                    }
                    T result = current.head;
                    current = current.tail;
                    return result;
                }
            };

        }

    }

}
