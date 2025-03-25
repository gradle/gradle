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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.matching.AttributeMatcher;
import org.gradle.internal.collections.ImmutableFilteredList;
import org.gradle.internal.model.InMemoryCacheFactory;
import org.gradle.internal.model.InMemoryLoadingCache;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds all artifact sets that can be created from a given set of producer artifact sets using
 * the consumer's artifact transforms. Transforms can be chained. If multiple
 * chains can lead to the same outcome, the shortest paths are selected.
 * <p>
 * This class is scoped to a given {@link AttributeMatcher} instance. This class may be used
 * by multiple projects and/or dependency resolution instances, each with different
 * registered transform sets, as long as they share a common attribute matcher.
 * <p>
 * Results are cached, as often the same request is made for many components in a
 * dependency graph. The cache is independent of the source artifact sets and registered
 * transform instances by storing the index of the input parameters as the cache key
 * and mapping input and output indices to the actual instances.
 */
public class ConsumerProvidedVariantFinder {

    private final AttributeMatcher matcher;
    private final AttributesFactory attributesFactory;
    private final TransformCache transformCache;

    public ConsumerProvidedVariantFinder(
        AttributeMatcher matcher,
        AttributesFactory attributesFactory,
        InMemoryCacheFactory cacheFactory
    ) {
        this.matcher = matcher;
        this.attributesFactory = attributesFactory;
        this.transformCache = new TransformCache(cacheFactory, this::doFindTransformedArtifactSets);
    }

    /**
     * Executes the transform chain detection algorithm given a set of registered transforms,
     * producer artifact sets, and the requested attributes. Only the transform chains of the shortest
     * depth are returned, and all results are guaranteed to have the same depth.
     *
     * @param registeredTransforms The transforms to select from.
     * @param sources The set of producer artifact sets.
     * @param requested The requested attributes.
     *
     * @return A collection of transform chains which, if applied to the corresponding source artifact set, will produce a
     *      artifact set compatible with the requested attributes.
     */
    public List<TransformedVariant> findCandidateTransformationChains(
        ImmutableAttributes requested,
        RegisteredTransforms registeredTransforms,
        List<ResolvedVariant> sources
    ) {
        CacheableTransforms transforms = registeredTransforms.getCacheableTransforms();
        List<ImmutableAttributes> sourceAttributes = new ArrayList<>(sources.size());
        for (ResolvedVariant source : sources) {
            sourceAttributes.add(source.getAttributes());
        }

        List<CachedTransformedArtifactSet> result = transformCache.query(requested, transforms, sourceAttributes);

        ImmutableList.Builder<TransformedVariant> output = ImmutableList.builderWithExpectedSize(result.size());
        for (CachedTransformedArtifactSet cachedArtifactSet : result) {
            output.add(getTransformedVariant(registeredTransforms, sources, cachedArtifactSet));
        }

        return output.build();
    }

    /**
     * Converts a single cached transformed artifact set into the project-specific representation.
     */
    private static TransformedVariant getTransformedVariant(
        RegisteredTransforms registeredTransforms,
        List<ResolvedVariant> sources,
        CachedTransformedArtifactSet cachedArtifactSet
    ) {
        ResolvedVariant source = sources.get(cachedArtifactSet.sourceIndex);
        ImmutableList<TransformRegistration> transforms = registeredTransforms.getTransforms();

        DefaultVariantDefinition previous = null;
        assert !cachedArtifactSet.chain.isEmpty();

        for (CachedTransformationStep step : cachedArtifactSet.chain) {
            TransformRegistration transform = transforms.get(step.registrationIndex);
            previous = new DefaultVariantDefinition(
                previous,
                step.attributes,
                transform.getTransformStep()
            );
        }

        return new TransformedVariant(source, previous);
    }

    /**
     * A node in a chain of artifact transforms.
     */
    private static class ChainNode {

        final ChainNode next;
        final CacheableTransforms.CacheableTransform transform;

        public ChainNode(@Nullable ChainNode next, CacheableTransforms.CacheableTransform transform) {
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
        final ImmutableFilteredList<CacheableTransforms.CacheableTransform> transforms;

        /**
         * @param chain The candidate transform chain.
         * @param requested The attribute set which must be produced by any previous artifact set in order to achieve the
         *      original user-requested attribute set after {@code chain} is applied to that previous artifact set.
         * @param transforms The remaining transforms which may be prepended to {@code chain} to produce a solution.
         */
        public ChainState(@Nullable ChainNode chain, ImmutableAttributes requested, ImmutableFilteredList<CacheableTransforms.CacheableTransform> transforms) {
            this.chain = chain;
            this.requested = requested;
            this.transforms = transforms;
        }

    }

    /**
     * A cached result of the transform chain detection algorithm. References an index within the source artifact set
     * list instead of an actual artifact set itself, so that this result can be cached and used for distinct solutions
     * that otherwise share the same attributes.
     */
    static class CachedTransformedArtifactSet {

        final int sourceIndex;
        final List<CachedTransformationStep> chain;

        public CachedTransformedArtifactSet(int sourceIndex, List<CachedTransformationStep> chain) {
            this.sourceIndex = sourceIndex;
            this.chain = chain;
        }

    }

    /**
     * A single step in an artifact transform solution, consisting of the index of the transform
     * in the original list of transforms, and the attributes of the artifact set produced as a result
     * of applying this step to the prior artifact set in the transformation chain.
     */
    static class CachedTransformationStep {

        final int registrationIndex;
        final ImmutableAttributes attributes;

        public CachedTransformationStep(int registrationIndex, ImmutableAttributes attributes) {
            this.registrationIndex = registrationIndex;
            this.attributes = attributes;
        }

    }

    /**
     * The algorithm itself. Performs a breadth-first search on the set of potential transform solutions in order to find
     * all solutions at a given transform chain depth. The search begins at the final node of the chain. At each depth, a candidate
     * transform is applied to the beginning of the chain. Then, if a source artifact set can be used as a root of that chain,
     * we have found a solution. Otherwise, if no solutions are found at this depth, we run the search at the next depth, with all
     * candidate transforms linked to the previous level's chains.
     */
    private List<CachedTransformedArtifactSet> doFindTransformedArtifactSets(
        ImmutableAttributes requested,
        CacheableTransforms transforms,
        List<ImmutableAttributes> sources
    ) {
        List<ChainState> toProcess = new ArrayList<>();
        List<ChainState> nextDepth = new ArrayList<>();
        toProcess.add(new ChainState(null, requested, ImmutableFilteredList.allOf(transforms.getTransforms())));

        List<CachedTransformedArtifactSet> results = new ArrayList<>(1);
        while (results.isEmpty() && !toProcess.isEmpty()) {
            for (ChainState state : toProcess) {
                // The set of transforms which could potentially produce an artifact set compatible with `requested`.
                ImmutableFilteredList<CacheableTransforms.CacheableTransform> candidates =
                    state.transforms.matching(transform -> matcher.isMatchingCandidate(transform.getTo(), state.requested));

                // For each candidate, attempt to find a source artifact set that the transform can use as its root.
                for (CacheableTransforms.CacheableTransform candidate : candidates) {
                    for (int i = 0; i < sources.size(); i++) {
                        ImmutableAttributes sourceAttrs = sources.get(i);
                        if (matcher.isMatchingCandidate(sourceAttrs, candidate.getFrom())) {
                            ImmutableAttributes rootAttrs = attributesFactory.concat(sourceAttrs, candidate.getTo());
                            if (matcher.isMatchingCandidate(rootAttrs, state.requested)) {
                                List<CachedTransformationStep> artifactSetChain = extractTransformIndices(candidate, rootAttrs, state.chain);
                                results.add(new CachedTransformedArtifactSet(i, artifactSetChain));
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
                    CacheableTransforms.CacheableTransform candidate = candidates.get(i);
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
     * Constructs a complete cacheable transformation chain given an initial transform and the chain of transforms
     * to apply after it.
     *
     * @param first The first transform to apply to the source artifact set.
     * @param firstAttrs The resulting attributes produced as a result of applying the first transform to the source artifact set.
     * @param rest The transform chain from the search state to apply to after the first transform.
     *
     * @return A list of transform steps, where each step corresponds to a transform registration in the original
     *      transform list. The steps are ordered such that the first step corresponds to the first transform to
     *      apply to the source artifact set.
     */
    private List<CachedTransformationStep> extractTransformIndices(
        CacheableTransforms.CacheableTransform first,
        ImmutableAttributes firstAttrs,
        @Nullable ChainNode rest
    ) {
        List<CachedTransformationStep> steps = new ArrayList<>();

        CachedTransformationStep firstStep = new CachedTransformationStep(first.getRegistrationIndex(), firstAttrs);
        steps.add(firstStep);

        ChainNode node = rest;
        CachedTransformationStep previous = firstStep;

        while (node != null) {
            ImmutableAttributes stepAttributes = attributesFactory.concat(previous.attributes, node.transform.getTo());
            CachedTransformationStep step = new CachedTransformationStep(node.transform.getRegistrationIndex(), stepAttributes);
            steps.add(step);
            node = node.next;
            previous = step;
        }

        return steps;
    }

    /**
     * Computes the transform chains that can be applied to any of the given source artifact sets to produce an artifact set
     * compatible with the requested attributes.
     */
    interface TransformCalculator {

        List<CachedTransformedArtifactSet> findTransforms(
            ImmutableAttributes requested,
            CacheableTransforms transforms,
            List<ImmutableAttributes> sources
        );

    }

    /**
     * Caches calls to the transform chain selection algorithm. The cached results are stored in
     * a transform-independent and source-independent manner, such that only the indices of the
     * registered transforms and input artifact sets are cached. This way, if multiple calls are made
     * with different transforms or artifact sets, but they have the same attributes, the cached results
     * may be used.
     */
    private static class TransformCache {

        private final TransformCalculator action;
        private final InMemoryLoadingCache<CacheKey, List<CachedTransformedArtifactSet>> cache;

        public TransformCache(InMemoryCacheFactory cacheFactory, TransformCalculator action) {
            this.action = action;
            this.cache = cacheFactory.create(this::doQuery);
        }

        public List<CachedTransformedArtifactSet> query(
            ImmutableAttributes requested,
            CacheableTransforms transforms,
            List<ImmutableAttributes> sourceAttributes
        ) {
            return cache.get(new CacheKey(requested, transforms, sourceAttributes));
        }

        private List<CachedTransformedArtifactSet> doQuery(CacheKey key) {
            return action.findTransforms(key.requested, key.transforms, key.sourceAttributes);
        }

        private static class CacheKey {

            private final CacheableTransforms transforms;
            private final List<ImmutableAttributes> sourceAttributes;
            private final ImmutableAttributes requested;

            private final int hashCode;

            public CacheKey(
                ImmutableAttributes requested,
                CacheableTransforms transforms,
                List<ImmutableAttributes> sourceAttributes
            ) {
                this.requested = requested;
                this.transforms = transforms;
                this.sourceAttributes = sourceAttributes;

                this.hashCode = computeHashCode(requested, transforms, sourceAttributes);
            }

            private static int computeHashCode(
                ImmutableAttributes requested,
                CacheableTransforms transforms,
                List<ImmutableAttributes> sourceAttributes
            ) {
                int result = requested.hashCode();
                result = 31 * result + transforms.hashCode();
                result = 31 * result + sourceAttributes.hashCode();
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

                if (requested != cacheKey.requested || // ImmutableAttributes instances are interned.
                    transforms != cacheKey.transforms || // CacheableTransforms instances are interned.
                    sourceAttributes.size() != cacheKey.sourceAttributes.size()
                ) {
                    return false;
                }

                int size = sourceAttributes.size();
                for (int i = 0; i < size; i++) {
                    // ImmutableAttributes instances are interned.
                    if (sourceAttributes.get(i) != cacheKey.sourceAttributes.get(i)) {
                        return false;
                    }
                }

                return true;
            }

            @Override
            public int hashCode() {
                return hashCode;
            }

        }

    }

}
