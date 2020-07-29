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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BrokenResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeDescriber;
import org.gradle.api.internal.attributes.AttributeValue;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.Cast;
import org.gradle.internal.Pair;
import org.gradle.internal.component.AmbiguousVariantSelectionException;
import org.gradle.internal.component.NoMatchingVariantSelectionException;
import org.gradle.internal.component.VariantSelectionException;
import org.gradle.internal.component.model.AttributeMatcher;
import org.gradle.internal.component.model.AttributeMatchingExplanationBuilder;
import org.gradle.internal.component.model.DescriberSelector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

class AttributeMatchingVariantSelector implements VariantSelector {
    private final ConsumerProvidedVariantFinder consumerProvidedVariantFinder;
    private final AttributesSchemaInternal schema;
    private final ImmutableAttributesFactory attributesFactory;
    private final TransformedVariantFactory transformedVariantFactory;
    private final ImmutableAttributes requested;
    private final boolean ignoreWhenNoMatches;
    private final ExtraExecutionGraphDependenciesResolverFactory dependenciesResolver;

    AttributeMatchingVariantSelector(
        ConsumerProvidedVariantFinder consumerProvidedVariantFinder,
        AttributesSchemaInternal schema,
        ImmutableAttributesFactory attributesFactory,
        TransformedVariantFactory transformedVariantFactory,
        AttributeContainerInternal requested,
        boolean ignoreWhenNoMatches,
        ExtraExecutionGraphDependenciesResolverFactory dependenciesResolver
    ) {
        this.consumerProvidedVariantFinder = consumerProvidedVariantFinder;
        this.schema = schema;
        this.attributesFactory = attributesFactory;
        this.transformedVariantFactory = transformedVariantFactory;
        this.requested = requested.asImmutable();
        this.ignoreWhenNoMatches = ignoreWhenNoMatches;
        this.dependenciesResolver = dependenciesResolver;
    }

    @Override
    public String toString() {
        return "Variant selector for " + requested;
    }

    @Override
    public ResolvedArtifactSet select(ResolvedVariantSet producer, Factory factory) {
        try {
            return doSelect(producer, factory, AttributeMatchingExplanationBuilder.logging());
        } catch (VariantSelectionException t) {
            return new BrokenResolvedArtifactSet(t);
        } catch (Exception t) {
            return new BrokenResolvedArtifactSet(VariantSelectionException.selectionFailed(producer, t));
        }
    }

    private ResolvedArtifactSet doSelect(ResolvedVariantSet producer, Factory factory, AttributeMatchingExplanationBuilder explanationBuilder) {
        AttributeMatcher matcher = schema.withProducer(producer.getSchema());
        ImmutableAttributes componentRequested = attributesFactory.concat(requested, producer.getOverriddenAttributes());
        List<? extends ResolvedVariant> matches = matcher.matches(producer.getVariants(), componentRequested, explanationBuilder);
        if (matches.size() == 1) {
            return matches.get(0).getArtifacts();
        }
        if (matches.size() > 1) {
            if (explanationBuilder instanceof TraceDiscardedVariants) {
                Set<ResolvedVariant> discarded = Cast.uncheckedCast(((TraceDiscardedVariants) explanationBuilder).discarded);
                AttributeDescriber describer = DescriberSelector.selectDescriber(componentRequested, schema);
                throw new AmbiguousVariantSelectionException(describer, producer.asDescribable().getDisplayName(), componentRequested, matches, matcher, discarded);
            } else {
                // because we're going to fail, we can afford a second run with details
                return doSelect(producer, factory, new TraceDiscardedVariants());
            }
        }

        List<Pair<ResolvedVariant, MutableConsumerVariantMatchResult.ConsumerVariant>> candidates = new ArrayList<>();
        for (ResolvedVariant variant : producer.getVariants()) {
            AttributeContainerInternal variantAttributes = variant.getAttributes().asImmutable();
            ConsumerVariantMatchResult matchResult = consumerProvidedVariantFinder.collectConsumerVariants(variantAttributes, componentRequested);
            for (MutableConsumerVariantMatchResult.ConsumerVariant consumerVariant : matchResult.getMatches()) {
                candidates.add(Pair.of(variant, consumerVariant));
            }
        }
        if (candidates.size() > 1) {
            candidates = tryDisambiguate(matcher, candidates, componentRequested, explanationBuilder);
        }
        if (candidates.size() == 1) {
            Pair<ResolvedVariant, MutableConsumerVariantMatchResult.ConsumerVariant> result = candidates.get(0);
            ResolvedVariant variant = result.getLeft();
            ImmutableAttributes attributes = result.getRight().attributes.asImmutable();
            Transformation transformation = result.getRight().transformation;
            return factory.asTransformed(variant, attributes, transformation, dependenciesResolver, transformedVariantFactory);
        }

        if (!candidates.isEmpty()) {
            throw new AmbiguousTransformException(producer.asDescribable().getDisplayName(), componentRequested, candidates);
        }

        if (ignoreWhenNoMatches) {
            return ResolvedArtifactSet.EMPTY;
        }
        throw new NoMatchingVariantSelectionException(producer.asDescribable().getDisplayName(), componentRequested, producer.getVariants(), matcher, DescriberSelector.selectDescriber(componentRequested, schema));
    }

    private List<Pair<ResolvedVariant, MutableConsumerVariantMatchResult.ConsumerVariant>> tryDisambiguate(AttributeMatcher matcher, List<Pair<ResolvedVariant, MutableConsumerVariantMatchResult.ConsumerVariant>> candidates, ImmutableAttributes componentRequested, AttributeMatchingExplanationBuilder explanationBuilder) {
        candidates = disambiguateWithSchema(matcher, candidates, componentRequested, explanationBuilder);

        if (candidates.size() == 1) {
            return candidates;
        }

        if (candidates.size() == 2) {
            // Short circuit logic when only 2 candidates
            return compareCandidates(matcher, candidates.get(0), candidates.get(1))
                .map(Collections::singletonList)
                .orElse(candidates);
        }

        List<Pair<ResolvedVariant, MutableConsumerVariantMatchResult.ConsumerVariant>> shortestTransforms = Lists.newArrayListWithExpectedSize(candidates.size());
        candidates.sort(Comparator.comparingInt(candidate -> candidate.right.depth));

        // Need to remember if a further element was matched by an earlier one, no need to consider it then
        boolean[] hasBetterMatch = new boolean[candidates.size()];

        for (int i = 0; i < candidates.size(); i++) {
            if (hasBetterMatch[i]) {
                continue;
            }
            boolean candidateIsDifferent = true;
            Pair<ResolvedVariant, MutableConsumerVariantMatchResult.ConsumerVariant> current = candidates.get(i);
            for (int j = i + 1; j < candidates.size(); j++) {
                if (hasBetterMatch[j]) {
                    continue;
                }
                int index = j; // Needed to use inside lambda below
                candidateIsDifferent = compareCandidates(matcher, current, candidates.get(index)).map(candidate -> {
                    if (candidate != current) {
                        // The other is better, current is not part of result
                        return false;
                    } else {
                        // The other is disambiguated by current, never consider other again
                        hasBetterMatch[index] = true;
                    }
                    return true;
                }).orElse(true);
            }
            if (candidateIsDifferent) {
                shortestTransforms.add(current);
            }
        }
        return shortestTransforms;
    }

    private List<Pair<ResolvedVariant, MutableConsumerVariantMatchResult.ConsumerVariant>> disambiguateWithSchema(AttributeMatcher matcher, List<Pair<ResolvedVariant, MutableConsumerVariantMatchResult.ConsumerVariant>> candidates, ImmutableAttributes componentRequested, AttributeMatchingExplanationBuilder explanationBuilder) {
        List<AttributeContainerInternal> candidateAttributes = candidates.stream().map(pair -> pair.getRight().attributes).collect(Collectors.toList());
        List<AttributeContainerInternal> matches = matcher.matches(candidateAttributes, componentRequested, explanationBuilder);
        if (matches.size() == 1) {
            AttributeContainerInternal singleMatch = matches.get(0);
            return candidates.stream().filter(pair -> pair.getRight().attributes.equals(singleMatch)).collect(Collectors.toList());
        } else if (matches.size() > 0 && matches.size() < candidates.size()) {
            // We know all are compatibles, so this is only possible if some disambiguation happens but not getting us to 1 candidate
            return candidates.stream().filter(pair -> matches.contains(pair.getRight().attributes)).collect(Collectors.toList());
        }
        return candidates;
    }

    private Optional<Pair<ResolvedVariant, MutableConsumerVariantMatchResult.ConsumerVariant>> compareCandidates(AttributeMatcher matcher,
                                                                                                                 Pair<ResolvedVariant, MutableConsumerVariantMatchResult.ConsumerVariant> firstCandidate,
                                                                                                                 Pair<ResolvedVariant, MutableConsumerVariantMatchResult.ConsumerVariant> secondCandidate) {

        if (matcher.isMatching(firstCandidate.right.attributes, secondCandidate.right.attributes) || matcher.isMatching(secondCandidate.right.attributes, firstCandidate.right.attributes)) {
            if (firstCandidate.right.depth >= secondCandidate.right.depth) {
                return Optional.of(secondCandidate);
            } else {
                return Optional.of(firstCandidate);
            }
        }
        return Optional.empty();
    }

    private static class TraceDiscardedVariants implements AttributeMatchingExplanationBuilder {

        private final Set<HasAttributes> discarded = Sets.newHashSet();

        @Override
        public boolean canSkipExplanation() {
            return false;
        }

        @Override
        public <T extends HasAttributes> void candidateDoesNotMatchAttributes(T candidate, AttributeContainerInternal requested) {
            recordDiscardedCandidate(candidate);
        }

        public <T extends HasAttributes> void recordDiscardedCandidate(T candidate) {
            discarded.add(candidate);
        }

        @Override
        public <T extends HasAttributes> void candidateAttributeDoesNotMatch(T candidate, Attribute<?> attribute, Object requestedValue, AttributeValue<?> candidateValue) {
            recordDiscardedCandidate(candidate);
        }
    }
}
