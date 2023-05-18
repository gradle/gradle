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
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.collections.ImmutableFilteredList;
import org.gradle.internal.component.model.AttributeMatcher;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
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
public class ConsumerProvidedVariantFinder {
    private final VariantTransformRegistry variantTransforms;
    private final ImmutableAttributesFactory attributesFactory;
    private final CachingAttributeMatcher matcher;
    private final TransformCache transformCache;

    public ConsumerProvidedVariantFinder(
        VariantTransformRegistry variantTransforms,
        AttributesSchemaInternal schema,
        ImmutableAttributesFactory attributesFactory
    ) {
        this.variantTransforms = variantTransforms;
        this.attributesFactory = attributesFactory;
        this.matcher = new CachingAttributeMatcher(schema.matcher());
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
    public List<TransformedVariant> findTransformedVariants(List<ResolvedVariant> sources, ImmutableAttributes requested) {
        return transformCache.query(sources, requested);
    }

    /**
     * A node in a chain of artifact transforms.
     */
    private static class ChainNode {
        final ChainNode next;
        final TransformRegistration transform;
        public ChainNode(@Nullable ChainNode next, TransformRegistration transform) {
            this.next = next;
            this.transform = transform;
        }
    }

    /**
     * Represents the intermediate state of a potential transform solution. Many instances of this state may simultaneously exist
     * for different potential solutions.
     */
    private static class ChainState {
        final ChainNode chain;
        final ImmutableAttributes requested;
        final ImmutableFilteredList<TransformRegistration> transforms;

        /**
         * @param chain The candidate transform chain.
         * @param requested The attribute set which must be produced by any previous variant in order to achieve the
         *      original user-requested attribute set after {@code chain} is applied to that previous variant.
         * @param transforms The remaining transforms which may be prepended to {@code chain} to produce a solution.
         */
        public ChainState(@Nullable ChainNode chain, ImmutableAttributes requested, ImmutableFilteredList<TransformRegistration> transforms) {
            this.chain = chain;
            this.requested = requested;
            this.transforms = transforms;
        }
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
     * The algorithm itself. Performs a breadth-first search on the set of potential transform solutions in order to find
     * all solutions at a given transform chain depth. The search begins at the final node of the chain. At each depth, a candidate
     * transform is applied to the beginning of the chain. Then, if a source variant can be used as a root of that chain,
     * we have found a solution. Otherwise, if no solutions are found at this depth, we run the search at the next depth, with all
     * candidate transforms linked to the previous level's chains.
     */
    private List<CachedVariant> doFindTransformedVariants(List<ImmutableAttributes> sources, ImmutableAttributes requested) {
        List<ChainState> toProcess = new ArrayList<>();
        List<ChainState> nextDepth = new ArrayList<>();
        toProcess.add(new ChainState(null, requested, ImmutableFilteredList.allOf(variantTransforms.getRegistrations())));

        List<CachedVariant> results = new ArrayList<>(1);
        while (results.isEmpty() && !toProcess.isEmpty()) {
            for (ChainState state : toProcess) {
                // The set of transforms which could potentially produce a variant compatible with `requested`.
                ImmutableFilteredList<TransformRegistration> candidates =
                    state.transforms.matching(transform -> matcher.isMatching(transform.getTo(), state.requested));

                // For each candidate, attempt to find a source variant that the transform can use as its root.
                for (TransformRegistration candidate : candidates) {
                    for (int i = 0; i < sources.size(); i++) {
                        ImmutableAttributes sourceAttrs = sources.get(i);
                        if (matcher.isMatching(sourceAttrs, candidate.getFrom())) {
                            ImmutableAttributes rootAttrs = attributesFactory.concat(sourceAttrs, candidate.getTo());
                            if (matcher.isMatching(rootAttrs, state.requested)) {
                                DefaultVariantDefinition rootTransformedVariant = new DefaultVariantDefinition(null, rootAttrs, candidate.getTransformStep());
                                VariantDefinition variantChain = createVariantChain(state.chain, rootTransformedVariant);
                                results.add(new CachedVariant(i, variantChain));
                            }
                        }
                    }
                }

                // If we have a result at this depth, don't bother building the next depth's states.
                if (!results.isEmpty()) {
                    continue;
                }

                // Construct new states for processing at the next depth in case we can't find any solutions at this depth.
                for (int i = 0; i < candidates.size(); i++) {
                    TransformRegistration candidate = candidates.get(i);
                    nextDepth.add(new ChainState(
                        new ChainNode(state.chain, candidate),
                        attributesFactory.concat(state.requested, candidate.getFrom()),
                        state.transforms.withoutIndexFrom(i, candidates)
                    ));
                }
            }

            toProcess.clear();
            List<ChainState> tmp = toProcess;
            toProcess = nextDepth;
            nextDepth = tmp;
        }

        return results;
    }

    /**
     * Constructs a complete cacheable variant chain given a root transformed variant and the chain of variants
     * to apply to that root variant.
     *
     * @param stateChain The transform chain from the search state to apply to the root transformed variant.
     * @param root The root variant to apply the chain to.
     *
     * @return A variant chain representing the final transformed variant.
     */
    private VariantDefinition createVariantChain(final ChainNode stateChain, DefaultVariantDefinition root) {
        ChainNode node = stateChain;
        DefaultVariantDefinition last = root;
        while (node != null) {
            last = new DefaultVariantDefinition(
                last,
                attributesFactory.concat(last.getTargetAttributes(), node.transform.getTo()),
                node.transform.getTransformStep()
            );
            node = node.next;
        }
        return last;
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
                variantAttributes.add(variant.getAttributes().asImmutable());
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
                this.hashCode = variantAttributes.hashCode() ^ requested.hashCode();
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
     * Caches calls to {@link AttributeMatcher#isMatching(AttributeContainerInternal, AttributeContainerInternal)}
     */
    private static class CachingAttributeMatcher {
        private final AttributeMatcher matcher;
        private final ConcurrentHashMap<CacheKey, Boolean> cache = new ConcurrentHashMap<>();

        public CachingAttributeMatcher(AttributeMatcher matcher) {
            this.matcher = matcher;
        }

        public boolean isMatching(AttributeContainerInternal candidate, AttributeContainerInternal requested) {
            return cache.computeIfAbsent(new CacheKey(candidate, requested), key -> matcher.isMatching(key.candidate, key.requested));
        }

        private static class CacheKey {
            private final AttributeContainerInternal candidate;
            private final AttributeContainerInternal requested;
            private final int hashCode;

            public CacheKey(AttributeContainerInternal candidate, AttributeContainerInternal requested) {
                this.candidate = candidate;
                this.requested = requested;
                this.hashCode = candidate.hashCode() ^ requested.hashCode();
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
                return candidate.equals(cacheKey.candidate) && requested.equals(cacheKey.requested);
            }

            @Override
            public int hashCode() {
                return hashCode;
            }
        }
    }
}
