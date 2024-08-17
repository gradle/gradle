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
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import org.apache.commons.lang3.function.TriFunction;
import org.gradle.api.internal.artifacts.TransformRegistration;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.attributes.matching.AttributeMatcher;
import org.gradle.internal.collections.ImmutableFilteredList;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finds all the variants that can be created from a given set of producer variants using
 * the consumer's variant transforms. Transforms can be chained. If multiple
 * chains can lead to the same outcome, the shortest paths are selected.
 * <p>
 * This class is scoped to a given {@link AttributeMatcher} instance. This class may be used
 * by multiple projects and/or dependency resolution instances, each with different
 * registered transform sets, as long as they share a common attribute matcher.
 * <p>
 * Results are cached, as often the same request is made for many components in a
 * dependency graph. The cache is independent of the source variant and registered
 * transform instances by storing the index of the input parameters as the cache key
 * and mapping input and output indices to the actual instances.
 */
public class ConsumerProvidedVariantFinder {

    private final AttributeMatcher matcher;
    private final ImmutableAttributesFactory attributesFactory;
    private final TransformCache transformCache;

    private final Interner<CachedTransforms> internedTransforms = Interners.newStrongInterner();

    public ConsumerProvidedVariantFinder(
        AttributeMatcher matcher,
        ImmutableAttributesFactory attributesFactory
    ) {
        this.matcher = matcher;
        this.attributesFactory = attributesFactory;
        this.transformCache = new TransformCache(this::doFindTransformedVariants);
    }

    public CachedTransforms cacheRegisteredTransforms(ImmutableList<TransformRegistration> transforms) {
        List<ConsumerProvidedVariantFinder.CachedTransform> cachedTransforms = new ArrayList<>(transforms.size());
        for (int i = 0; i < transforms.size(); i++) {
            TransformRegistration registration = transforms.get(i);
            cachedTransforms.add(new ConsumerProvidedVariantFinder.CachedTransform(
                i,
                registration.getFrom(),
                registration.getTo()
            ));
        }

        return internedTransforms.intern(new ConsumerProvidedVariantFinder.CachedTransforms(cachedTransforms));
    }

    /**
     * Executes the transform chain detection algorithm given a set of registered transforms,
     * producer variants and the requested attributes. Only the transform chains of the shortest
     * depth are returned, and all results are guaranteed to have the same depth.
     *
     * @param variantTransforms The transforms to select from.
     * @param sources The set of producer variants.
     * @param requested The requested attributes.
     *
     * @return A collection of variant chains which, if applied to the corresponding source variant, will produce a
     *      variant compatible with the requested attributes.
     */
    public List<CachedVariant> findTransformedVariants(
        ImmutableAttributes requested,
        CachedTransforms variantTransforms,
        List<ResolvedVariant> sources
    ) {
        return transformCache.query(requested, variantTransforms, sources);
    }

    static class CachedTransforms {
        private final List<CachedTransform> transforms;
        private final int hashCode;

        public CachedTransforms(List<CachedTransform> transforms) {
            this.transforms = transforms;
            this.hashCode = transforms.hashCode();
        }

        public List<CachedTransform> asList() {
            return transforms;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            CachedTransforms that = (CachedTransforms) o;
            return transforms.equals(that.transforms);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    /**
     * Represents a transform step in the transform chain detection algorithm. This class is used in the cached
     * result and only refers to the index of the source transform in the original registered transform list.
     */
    static class CachedTransform {
        private final int registrationIndex;
        private final ImmutableAttributes from;
        private final ImmutableAttributes to;
        public CachedTransform(int registrationIndex, ImmutableAttributes from, ImmutableAttributes to) {
            this.registrationIndex = registrationIndex;
            this.from = from;
            this.to = to;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            CachedTransform that = (CachedTransform) o;
            return registrationIndex == that.registrationIndex &&
                from.equals(that.from) &&
                to.equals(that.to);
        }

        @Override
        public int hashCode() {
            int result = registrationIndex;
            result = 31 * result + from.hashCode();
            result = 31 * result + to.hashCode();
            return result;
        }
    }

    /**
     * A node in a chain of artifact transforms.
     */
    private static class ChainNode {
        final ChainNode next;
        final CachedTransform transform;
        public ChainNode(@Nullable ChainNode next, CachedTransform transform) {
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
        final ImmutableFilteredList<CachedTransform> transforms;

        /**
         * @param chain The candidate transform chain.
         * @param requested The attribute set which must be produced by any previous variant in order to achieve the
         *      original user-requested attribute set after {@code chain} is applied to that previous variant.
         * @param transforms The remaining transforms which may be prepended to {@code chain} to produce a solution.
         */
        public ChainState(@Nullable ChainNode chain, ImmutableAttributes requested, ImmutableFilteredList<CachedTransform> transforms) {
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
    static class CachedVariant {
        final int sourceIndex;
        final List<Integer> chain;
        public CachedVariant(int sourceIndex, List<Integer> chain) {
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
    private List<CachedVariant> doFindTransformedVariants(
        ImmutableAttributes requested,
        CachedTransforms transforms,
        List<ImmutableAttributes> sources
    ) {
        List<ChainState> toProcess = new ArrayList<>();
        List<ChainState> nextDepth = new ArrayList<>();
        toProcess.add(new ChainState(null, requested, ImmutableFilteredList.allOf(transforms.asList())));

        List<CachedVariant> results = new ArrayList<>(1);
        while (results.isEmpty() && !toProcess.isEmpty()) {
            for (ChainState state : toProcess) {
                // The set of transforms which could potentially produce a variant compatible with `requested`.
                ImmutableFilteredList<CachedTransform> candidates =
                    state.transforms.matching(transform -> matcher.isMatchingCandidate(transform.to, state.requested));

                // For each candidate, attempt to find a source variant that the transform can use as its root.
                for (CachedTransform candidate : candidates) {
                    for (int i = 0; i < sources.size(); i++) {
                        ImmutableAttributes sourceAttrs = sources.get(i);
                        if (matcher.isMatchingCandidate(sourceAttrs, candidate.from)) {
                            ImmutableAttributes rootAttrs = attributesFactory.concat(sourceAttrs, candidate.to);
                            if (matcher.isMatchingCandidate(rootAttrs, state.requested)) {
                                List<Integer> variantChain = extractTransformIndices(candidate, state.chain);
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
                    CachedTransform candidate = candidates.get(i);
                    nextDepth.add(new ChainState(
                        new ChainNode(state.chain, candidate),
                        attributesFactory.concat(state.requested, candidate.from),
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
     * Constructs a complete cacheable variant chain given an initial transform and the chain of transforms
     * to apply after it.
     *
     * @param first The first transform to apply to the source variant.
     * @param rest The transform chain from the search state to apply to after the first transform.
     *
     * @return A list of transform indices, where each index corresponds to a transform registration in the original
     *      transform list. The indices are ordered such that the first index corresponds to the first transform to
     *      apply to the source variant.
     */
    private static List<Integer> extractTransformIndices(CachedTransform first, @Nullable ChainNode rest) {
        List<Integer> indices = new ArrayList<>();
        indices.add(first.registrationIndex);

        ChainNode node = rest;
        while (node != null) {
            indices.add(node.transform.registrationIndex);
            node = node.next;
        }

        return indices;
    }

    /**
     * Caches calls to the transform chain selection algorithm. The cached results are stored in
     * a transform-independent and variant-independent manner, such that only the indices of the
     * registered transforms and input variants are cached. This way, if multiple calls are made
     * with different transforms or variants, but they have the same attributes, the cached results
     * may be used.
     */
    private static class TransformCache {

        private final TriFunction<ImmutableAttributes, CachedTransforms, List<ImmutableAttributes>, List<CachedVariant>> action;

        private final ConcurrentHashMap<CacheKey, List<CachedVariant>> cache = new ConcurrentHashMap<>();

        public TransformCache(
            TriFunction<ImmutableAttributes, CachedTransforms, List<ImmutableAttributes>, List<CachedVariant>> action
        ) {
            this.action = action;
        }

        private List<CachedVariant> query(
            ImmutableAttributes requested,
            CachedTransforms transforms,
            List<ResolvedVariant> sources
        ) {
            CacheKey query = createQuery(requested, transforms, sources);

            return cache.computeIfAbsent(query, key ->
                action.apply(key.requested, key.transforms, key.variantAttributes)
            );

            // TODO: Extract result early.
            // Merge the attributes but do not pull out transform indices.
        }

        private static CacheKey createQuery(ImmutableAttributes requested, CachedTransforms transforms, List<ResolvedVariant> sources) {

            List<ImmutableAttributes> variantAttributes = new ArrayList<>(sources.size());
            for (ResolvedVariant variant : sources) {
                variantAttributes.add(variant.getAttributes().asImmutable());
            }

            return new CacheKey(requested, transforms, variantAttributes);
        }

        private static class CacheKey {
            private final CachedTransforms transforms;
            private final List<ImmutableAttributes> variantAttributes;
            private final ImmutableAttributes requested;

            private final int hashCode;

            public CacheKey(
                ImmutableAttributes requested,
                CachedTransforms transforms,
                List<ImmutableAttributes> variantAttributes
            ) {
                this.requested = requested;
                this.transforms = transforms;
                this.variantAttributes = variantAttributes;

                this.hashCode = computeHashCode(requested, transforms, variantAttributes);
            }

            private static int computeHashCode(
                ImmutableAttributes requested,
                CachedTransforms transforms,
                List<ImmutableAttributes> variantAttributes
            ) {
                int result = requested.hashCode();
                result = 31 * result + transforms.hashCode();
                result = 31 * result + variantAttributes.hashCode();
                return result;
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
                return requested.equals(cacheKey.requested) &&
                    transforms == cacheKey.transforms && // We expect the transforms to be interned
                    variantAttributes.equals(cacheKey.variantAttributes);
            }

            @Override
            public int hashCode() {
                return hashCode;
            }
        }
    }
}
