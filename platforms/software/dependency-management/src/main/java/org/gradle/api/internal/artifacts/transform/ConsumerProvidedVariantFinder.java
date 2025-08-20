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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * Finds all the variants that can be created from a given set of producer variants using
 * the consumer's variant transforms. Transforms can be chained. If multiple
 * chains can lead to the same outcome, the shortest paths are selected.
 *
 * Caches the results, as often the same request is made for many components in a
 * dependency graph.
 */
@ServiceScope(Scope.Project.class)
public class ConsumerProvidedVariantFinder {
    private final VariantTransformRegistry variantTransforms;
    private final AttributesFactory attributesFactory;
    private final Lazy<AttributeMatcher> matcher;
    private final TransformCache transformCache;

    public ConsumerProvidedVariantFinder(
        VariantTransformRegistry variantTransforms,
        AttributesSchemaInternal schema,
        AttributesFactory attributesFactory,
        AttributeSchemaServices attributeSchemaServices
    ) {
        this.variantTransforms = variantTransforms;
        this.attributesFactory = attributesFactory;
        this.matcher = Lazy.locking().of(() -> {
            // TODO: This is incorrect. We fail to merge the consumer schema with the producer schema
            // and therefore we miss producer rules when matching transforms.
            // Instead, this class should be refactored to accept a matcher as a parameter,
            // where the matcher has already been created with the consumer and producer schema.
            ImmutableAttributesSchema immutable = attributeSchemaServices.getSchemaFactory().create(schema);
            return attributeSchemaServices.getMatcher(immutable, ImmutableAttributesSchema.EMPTY);
        });
        this.transformCache = new TransformCache(this::doFindTransformedVariants);
    }

    /**
     * Executes the transform chain detection algorithm given a set of producer variants and the requested
     * attributes. Only the transform chains of the shortest depth are returned, and all results are
     * guaranteed to have the same depth.
     *
     * @param sources The set of producer variants.
     * @param requested The requested attributes.
     *
     * @return A collection of variant chains which, if applied to the corresponding source variant, will produce a
     *      variant compatible with the requested attributes.
     */
    public List<TransformedVariant> findCandidateTransformationChains(List<ResolvedVariant> sources, ImmutableAttributes requested) {
        return transformCache.query(sources, requested);
    }

    /**
     * A cached result of the transform chain detection algorithm. References an index within the source variant
     * list instead of an actual variant itself, so that this result can be cached and used for distinct variant sets
     * that otherwise share the same attributes.
     */
    private static class CachedVariant {
        private final int sourceIndex;
        private final VariantDefinition chain;
        public CachedVariant(int sourceIndex, VariantDefinition chain) {
            this.sourceIndex = sourceIndex;
            this.chain = chain;
        }
    }

    /**
     * OPTIMIZED ALGORITHM: Reduces complexity from O(n!) to O(n² × m) where:
     * - n = number of transforms
     * - m = number of unique attribute states
     *
     * Key optimizations:
     * 1. State memoization to avoid redundant path exploration
     * 2. Early termination when solutions are found
     * 3. Transform deduplication within chains
     * 4. Forward search from sources instead of backward from target
     */
    private List<CachedVariant> doFindTransformedVariants(List<ImmutableAttributes> sources, ImmutableAttributes requested) {
        AttributeMatcher attributeMatcher = matcher.get();
        List<TransformRegistration> allTransforms = new ArrayList<>(variantTransforms.getRegistrations());

        List<CachedVariant> results = new ArrayList<>(1);

        // Check for direct matches (no transformation needed)
        for (int i = 0; i < sources.size(); i++) {
            if (attributeMatcher.isMatchingCandidate(sources.get(i), requested)) {
                results.add(new CachedVariant(i, null));
            }
        }

        if (!results.isEmpty()) {
            return results;
        }

        // Forward BFS with state memoization
        Map<StateKey, Integer> visitedStates = new HashMap<>();
        Queue<OptimizedState> currentLevel = new LinkedList<>();

        // Initialize with source variants
        for (int i = 0; i < sources.size(); i++) {
            OptimizedState initial = new OptimizedState(sources.get(i), i, new ArrayList<>(), new HashSet<>());
            currentLevel.offer(initial);
            visitedStates.put(new StateKey(sources.get(i), i), 0);
        }

        // Limit search depth to prevent infinite loops
        final int maxDepth = Math.min(allTransforms.size(), 10);

        for (int depth = 1; depth <= maxDepth && results.isEmpty(); depth++) {
            Queue<OptimizedState> nextLevel = new LinkedList<>();

            while (!currentLevel.isEmpty()) {
                OptimizedState current = currentLevel.poll();

                // Try applying each transform
                for (TransformRegistration transform : allTransforms) {
                    // Skip if we've already used this transform in this chain
                    if (current.usedTransforms.contains(transform)) {
                        continue;
                    }

                    // Check if transform is applicable to current attributes
                    if (!attributeMatcher.isMatchingCandidate(current.attributes, transform.getFrom())) {
                        continue;
                    }

                    // Calculate resulting attributes
                    ImmutableAttributes newAttributes = attributesFactory.concat(current.attributes, transform.getTo());

                    // Check if this produces a solution
                    if (attributeMatcher.isMatchingCandidate(newAttributes, requested)) {
                        List<TransformRegistration> chain = new ArrayList<>(current.transformChain);
                        chain.add(transform);
                        results.add(createOptimizedCachedVariant(sources.get(current.sourceIndex), chain, current.sourceIndex));
                    } else {
                        // Only explore if we haven't visited this state at an earlier depth
                        StateKey key = new StateKey(newAttributes, current.sourceIndex);
                        Integer previousDepth = visitedStates.get(key);
                        if (previousDepth == null || previousDepth > depth) {
                            Set<TransformRegistration> newUsedTransforms = new HashSet<>(current.usedTransforms);
                            newUsedTransforms.add(transform);
                            List<TransformRegistration> newChain = new ArrayList<>(current.transformChain);
                            newChain.add(transform);
                            OptimizedState newState = new OptimizedState(
                                newAttributes,
                                current.sourceIndex,
                                newChain,
                                newUsedTransforms
                            );
                            nextLevel.offer(newState);
                            visitedStates.put(key, depth);
                        }
                    }
                }
            }

            currentLevel = nextLevel;
        }

        return results;
    }

    /**
     * Creates a cached variant from an optimized forward search.
     */
    private CachedVariant createOptimizedCachedVariant(ImmutableAttributes source, List<TransformRegistration> chain, int sourceIndex) {
        if (chain.isEmpty()) {
            return new CachedVariant(sourceIndex, null);
        }

        DefaultVariantDefinition current = new DefaultVariantDefinition(
            null,
            attributesFactory.concat(source, chain.get(0).getTo()),
            chain.get(0).getTransformStep()
        );

        for (int i = 1; i < chain.size(); i++) {
            TransformRegistration transform = chain.get(i);
            current = new DefaultVariantDefinition(
                current,
                attributesFactory.concat(current.getTargetAttributes(), transform.getTo()),
                transform.getTransformStep()
            );
        }

        return new CachedVariant(sourceIndex, current);
    }

    /**
     * Optimized state representation for forward search.
     */
    private static class OptimizedState {
        final ImmutableAttributes attributes;
        final int sourceIndex;
        final List<TransformRegistration> transformChain;
        final Set<TransformRegistration> usedTransforms;

        OptimizedState(ImmutableAttributes attributes, int sourceIndex,
                      List<TransformRegistration> transformChain,
                      Set<TransformRegistration> usedTransforms) {
            this.attributes = attributes;
            this.sourceIndex = sourceIndex;
            this.transformChain = transformChain;
            this.usedTransforms = usedTransforms;
        }
    }

    /**
     * Key for state memoization - uniquely identifies a search state.
     */
    private static class StateKey {
        final ImmutableAttributes attributes;
        final int sourceIndex;

        StateKey(ImmutableAttributes attributes, int sourceIndex) {
            this.attributes = attributes;
            this.sourceIndex = sourceIndex;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof StateKey)) return false;
            StateKey stateKey = (StateKey) o;
            return sourceIndex == stateKey.sourceIndex &&
                   attributes.equals(stateKey.attributes);
        }

        @Override
        public int hashCode() {
            return 31 * attributes.hashCode() + sourceIndex;
        }
    }

    /**
     * Caches calls to the transform chain selection algorithm. The cached results are stored in
     * a variant-independent manner, such that only the attributes of the input variants are cached.
     * This way, if multiple calls are made with different variants but those variants have the same
     * attributes, the cached results may be used.
     */
    private static class TransformCache {
        private final ConcurrentHashMap<CacheKey, List<CachedVariant>> cache = new ConcurrentHashMap<>();
        private final BiFunction<List<ImmutableAttributes>, ImmutableAttributes, List<CachedVariant>> action;

        public TransformCache(BiFunction<List<ImmutableAttributes>, ImmutableAttributes, List<CachedVariant>> action) {
            this.action = action;
        }

        private List<TransformedVariant> query(
            List<ResolvedVariant> sources, ImmutableAttributes requested
        ) {
            List<ImmutableAttributes> variantAttributes = new ArrayList<>(sources.size());
            for (ResolvedVariant variant : sources) {
                variantAttributes.add(variant.getAttributes());
            }
            List<CachedVariant> cached = cache.computeIfAbsent(new CacheKey(variantAttributes, requested), key -> action.apply(key.variantAttributes, key.requested));
            List<TransformedVariant> output = new ArrayList<>(cached.size());
            for (CachedVariant variant : cached) {
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
}
