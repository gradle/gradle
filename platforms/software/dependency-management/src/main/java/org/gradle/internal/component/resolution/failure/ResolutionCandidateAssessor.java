/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.component.resolution.failure;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.gradle.api.Describable;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.NodeState;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeValue;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Cast;
import org.gradle.internal.component.ResolutionFailureHandler;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.model.AttributeMatcher;
import org.gradle.internal.component.model.GraphSelectionCandidates;
import org.gradle.internal.component.model.VariantGraphResolveMetadata;
import org.gradle.internal.component.model.VariantGraphResolveState;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A utility class used by {@link ResolutionFailureHandler} to assess and classify
 * how the attributes of candidate variants during a single attempt at dependency resolution
 * align with the requested attributes.
 */
public final class ResolutionCandidateAssessor {
    private final ImmutableAttributes requestedAttributes;
    private final AttributeMatcher attributeMatcher;

    public ResolutionCandidateAssessor(AttributeContainerInternal requestedAttributes, AttributeMatcher attributeMatcher) {
        this.requestedAttributes = requestedAttributes.asImmutable();
        this.attributeMatcher = attributeMatcher;
    }

    public ImmutableAttributes getRequestedAttributes() {
        return requestedAttributes;
    }

    public List<AssessedCandidate> assessVariantMetadatas(List<? extends VariantGraphResolveMetadata> variantMetadatas) {
        return variantMetadatas.stream()
            .map(variantMetadata -> assessCandidate(variantMetadata.getName(), variantMetadata.getCapabilities(), variantMetadata.getAttributes()))
            .sorted(Comparator.comparing(AssessedCandidate::getDisplayName))
            .collect(Collectors.toList());
    }

    public List<AssessedCandidate> assessResolvedVariants(List<? extends ResolvedVariant> resolvedVariants) {
        return resolvedVariants.stream()
            .map(variant -> assessCandidate(variant.asDescribable().getCapitalizedDisplayName(), variant.getCapabilities(), variant.getAttributes().asImmutable()))
            .sorted(Comparator.comparing(AssessedCandidate::getDisplayName))
            .collect(Collectors.toList());
    }

    public List<AssessedCandidate> assessResolvedVariantStates(List<? extends VariantGraphResolveState> variantStates) {
        return variantStates.stream()
            .map(VariantGraphResolveState::getMetadata)
            .map(variant -> assessCandidate(variant.getName(), variant.getCapabilities(), variant.getAttributes().asImmutable()))
            .sorted(Comparator.comparing(AssessedCandidate::getDisplayName))
            .collect(Collectors.toList());
    }

    public List<AssessedCandidate> assessNodeStates(Set<NodeState> nodes) {
        return nodes.stream()
            .map(NodeState::getMetadata)
            .map(variant -> assessCandidate(variant.getName(), variant.getCapabilities(), variant.getAttributes().asImmutable()))
            .sorted(Comparator.comparing(AssessedCandidate::getDisplayName))
            .collect(Collectors.toList());
    }

    public List<AssessedCandidate> assessGraphSelectionCandidates(GraphSelectionCandidates candidates) {
        return candidates.getVariantsForAttributeMatching().stream()
            .map(VariantGraphResolveState::getMetadata)
            .map(variantMetadata -> assessCandidate(variantMetadata.getName(), variantMetadata.getCapabilities(), variantMetadata.getAttributes()))
            .sorted(Comparator.comparing(AssessedCandidate::getDisplayName))
            .collect(Collectors.toList());
    }

    public AssessedCandidate assessCandidate(
        String candidateName,
        ImmutableCapabilities candidateCapabilities,
        ImmutableAttributes candidateAttributes
    ) {
        Set<String> alreadyAssessed = new HashSet<>(candidateAttributes.keySet().size());
        ImmutableList.Builder<AssessedAttribute<?>> compatible = ImmutableList.builder();
        ImmutableList.Builder<AssessedAttribute<?>> incompatible = ImmutableList.builder();
        ImmutableList.Builder<AssessedAttribute<?>> onlyOnConsumer = ImmutableList.builder();
        ImmutableList.Builder<AssessedAttribute<?>> onlyOnProducer = ImmutableList.builder();

        Sets.union(requestedAttributes.getAttributes().keySet(), candidateAttributes.getAttributes().keySet()).stream()
            .sorted(Comparator.comparing(Attribute::getName))
            .forEach(attribute -> classifyAttribute(requestedAttributes, candidateAttributes, attributeMatcher, attribute, alreadyAssessed, compatible, incompatible, onlyOnConsumer, onlyOnProducer));

        return new AssessedCandidate(candidateName, candidateAttributes, candidateCapabilities, compatible.build(), incompatible.build(), onlyOnConsumer.build(), onlyOnProducer.build());
    }

    private void classifyAttribute(ImmutableAttributes requestedAttributes, ImmutableAttributes candidateAttributes, AttributeMatcher attributeMatcher,
                                   Attribute<?> attribute, Set<String> alreadyAssessed,
                                   ImmutableList.Builder<AssessedAttribute<?>> compatible, ImmutableList.Builder<AssessedAttribute<?>> incompatible,
                                   ImmutableList.Builder<AssessedAttribute<?>> onlyOnConsumer, ImmutableList.Builder<AssessedAttribute<?>> onlyOnProducer) {
        if (alreadyAssessed.add(attribute.getName())) {
            Attribute<Object> untyped = Cast.uncheckedCast(attribute);
            String attributeName = attribute.getName();
            AttributeValue<?> consumerValue = requestedAttributes.findEntry(attributeName);
            AttributeValue<?> producerValue = candidateAttributes.findEntry(attributeName);

            if (consumerValue.isPresent() && producerValue.isPresent()) {
                if (attributeMatcher.isMatching(untyped, producerValue.coerce(attribute), consumerValue.coerce(attribute))) {
                    compatible.add(new AssessedAttribute<>(attribute, Cast.uncheckedCast(consumerValue.get()), Cast.uncheckedCast(producerValue.get())));
                } else {
                    incompatible.add(new AssessedAttribute<>(attribute, Cast.uncheckedCast(consumerValue.get()), Cast.uncheckedCast(producerValue.get())));
                }
            } else if (consumerValue.isPresent()) {
                onlyOnConsumer.add(new AssessedAttribute<>(attribute, Cast.uncheckedCast(consumerValue.get()), null));
            } else if (producerValue.isPresent()) {
                onlyOnProducer.add(new AssessedAttribute<>(attribute, null, Cast.uncheckedCast(producerValue.get())));
            }
        }
    }

    /**
     * An immutable data class that holds information about a single variant which was a candidate for matching during resolution.
     *
     * This includes classifying its attributes into lists of compatible, incompatible, and absent attributes.  Each candidate
     * is assessed within the context of a resolution, but must not reference the assessor
     * that produced it, in order to remain configuration cache compatible - the assessor is not serializable.
     */
    public static final class AssessedCandidate implements Describable {
        private final String name;
        private final ImmutableAttributes candidateAttributes;
        private final ImmutableCapabilities candidateCapabilities;

        private final ImmutableList<AssessedAttribute<?>> compatible;
        private final ImmutableList<AssessedAttribute<?>> incompatible;
        private final ImmutableList<AssessedAttribute<?>> onlyOnRequest;
        private final ImmutableList<AssessedAttribute<?>> onlyOnCandidate;

        private AssessedCandidate(String name, AttributeContainerInternal attributes, ImmutableCapabilities candidateCapabilities, ImmutableList<AssessedAttribute<?>> compatible, ImmutableList<AssessedAttribute<?>> incompatible, ImmutableList<AssessedAttribute<?>> onlyOnRequest, ImmutableList<AssessedAttribute<?>> onlyOnCandidate) {
            this.name = name;
            this.candidateAttributes = attributes.asImmutable();
            this.candidateCapabilities = candidateCapabilities;
            this.compatible = compatible;
            this.incompatible = incompatible;
            this.onlyOnRequest = onlyOnRequest;
            this.onlyOnCandidate = onlyOnCandidate;
        }

        @Override
        public String getDisplayName() {
            return name;
        }

        public ImmutableAttributes getAllCandidateAttributes() {
            return candidateAttributes;
        }

        public ImmutableCapabilities getCandidateCapabilities() {
            return candidateCapabilities;
        }

        public ImmutableList<AssessedAttribute<?>> getCompatibleAttributes() {
            return compatible;
        }

        public ImmutableList<AssessedAttribute<?>> getIncompatibleAttributes() {
            return incompatible;
        }

        public ImmutableList<AssessedAttribute<?>> getOnlyOnRequestAttributes() {
            return onlyOnRequest;
        }

        public ImmutableList<AssessedAttribute<?>> getOnlyOnCandidateAttributes() {
            return onlyOnCandidate;
        }

        public boolean hasNoAttributes() {
            return getAllCandidateAttributes().isEmpty();
        }
    }

    /**
     * An immutable data class that records a single attribute, its requested value, and its provided value
     * for a given resolution attempt.
     */
    public static final class AssessedAttribute<T> {
        private final Attribute<T> attribute;

        @Nullable
        private final T requested;
        @Nullable
        private final T provided;

        private AssessedAttribute(Attribute<T> attribute, @Nullable T requested, @Nullable T provided) {
            this.attribute = attribute;
            this.requested = requested;
            this.provided = provided;
        }

        public Attribute<T> getAttribute() {
            return attribute;
        }

        @Nullable
        public T getRequested() {
            return requested;
        }

        @Nullable
        public T getProvided() {
            return provided;
        }

        @Override
        public String toString() {
            return "{name=" + attribute.getName() +
                ", type=" + attribute.getType() +
                ", requested=" + requested +
                ", provided=" + provided +
                '}';
        }
    }
}
