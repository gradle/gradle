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

package org.gradle.internal.component;

import com.google.common.collect.Sets;
import org.gradle.api.Describable;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.plugin.GradlePluginApiVersion;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeValue;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Cast;
import org.gradle.internal.component.model.AttributeMatcher;
import org.gradle.internal.component.model.GraphSelectionCandidates;
import org.gradle.internal.component.model.VariantGraphResolveMetadata;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.util.GradleVersion;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A utility class used by {@link ResolutionFailureHandler} to assess and classify
 * how the attributes of candidate variants during dependency resolution align with the
 * requested attributes.
 */
/* package */ final class ResolutionCandidateAssessor {
    private final ImmutableAttributes requestedAttributes;
    private final AttributeMatcher attributeMatcher;

    public ResolutionCandidateAssessor(AttributeContainerInternal requestedAttributes, AttributeMatcher attributeMatcher) {
        this.requestedAttributes = requestedAttributes.asImmutable();
        this.attributeMatcher = attributeMatcher;
    }

    /**
     * Extracts variant metadata from the given {@link GraphSelectionCandidates}.
     *
     * @param candidates the candidates to extract variants from
     * @return the extracted variants, sorted by name
     */
    public List<? extends VariantGraphResolveMetadata> extractVariants(GraphSelectionCandidates candidates) {
        final List<? extends VariantGraphResolveMetadata> variants;
        if (candidates.isUseVariants()) {
            variants = candidates.getVariants().stream()
                .map(VariantGraphResolveState::getMetadata)
                .collect(Collectors.toList());
        } else {
            variants = candidates.getCandidateConfigurations();
        }

        variants.sort(Comparator.comparing(VariantGraphResolveMetadata::getName));
        return variants;
    }

    public List<AssessedCandidate> assessCandidates(List<? extends VariantGraphResolveMetadata> candidates) {
        return candidates.stream()
            .map(variantMetadata -> assessCandidate(variantMetadata.getName(), variantMetadata.getAttributes().asImmutable()))
            .collect(Collectors.toList());
    }

    public AssessedCandidate assessCandidate(
        String candidateName,
        ImmutableAttributes candidateAttributes
    ) {
        AssessedCandidate assessedCandidate = new AssessedCandidate(candidateName);
        Sets.union(requestedAttributes.getAttributes().keySet(), candidateAttributes.getAttributes().keySet()).stream()
            .sorted(Comparator.comparing(Attribute::getName))
            .forEach(attribute -> assessAttribute(candidateAttributes, attribute, assessedCandidate));
        return assessedCandidate;
    }

    private void assessAttribute(ImmutableAttributes immutableProducer, Attribute<?> attribute, AssessedCandidate assessedCandidate) {
        Attribute<Object> untyped = Cast.uncheckedCast(attribute);

        if (!assessedCandidate.alreadyAssessed(untyped)) {
            String attributeName = Objects.requireNonNull(attribute).getName();
            AttributeValue<?> consumerValue = requestedAttributes.findEntry(attributeName);
            AttributeValue<?> producerValue = immutableProducer.findEntry(attributeName);

            if (consumerValue.isPresent() && producerValue.isPresent()) {
                if (attributeMatcher.isMatching(untyped, producerValue.coerce(attribute), consumerValue.coerce(attribute))) {
                    assessedCandidate.addCompatible(new AssessedAttribute<>(attribute, Cast.uncheckedCast(consumerValue.get()), Cast.uncheckedCast(producerValue.get())));
                } else {
                    assessedCandidate.addIncompatible(new AssessedAttribute<>(attribute, Cast.uncheckedCast(consumerValue.get()), Cast.uncheckedCast(producerValue.get())));
                }
            } else if (consumerValue.isPresent()) {
                assessedCandidate.addOnlyOnConsumer(new AssessedAttribute<>(attribute, Cast.uncheckedCast(consumerValue.get()), null));
            } else if (producerValue.isPresent()) {
                assessedCandidate.addOnlyOnProducer(new AssessedAttribute<>(attribute, null, Cast.uncheckedCast(producerValue.get())));
            }
        }
    }

    public boolean isPluginRequestUsingApiVersionAttribute(AttributeContainerInternal consumerAttributes) {
        return consumerAttributes.contains(GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE);
    }

    public Optional<String> findHigherRequiredVersionOfGradle(GraphSelectionCandidates candidates) {
        List<? extends VariantGraphResolveMetadata> candidateMetadatas = extractVariants(candidates);
        List<AssessedCandidate> categorizedAttributes = assessCandidates(candidateMetadatas);
        return findIncompatibleRequiredMinimumVersionOfGradle(categorizedAttributes);
    }

    private Optional<String> findIncompatibleRequiredMinimumVersionOfGradle(List<AssessedCandidate> candidates) {
        String requiredMinimumVersionOfGradle = null;

        for (AssessedCandidate candidate : candidates) {
            Optional<GradleVersion> providedVersion = candidate.getIncompatibleAttributes().stream()
                .filter(incompatibleAttribute -> incompatibleAttribute.getAttribute().getName().equals(GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE.getName()))
                .map(apiVersionAttribute -> GradleVersion.version(String.valueOf(apiVersionAttribute.getRequested())))
                .findFirst();

            if (providedVersion.isPresent()) {
                GradleVersion version = providedVersion.get();
                if (requiredMinimumVersionOfGradle == null || version.compareTo(GradleVersion.version(requiredMinimumVersionOfGradle)) > 0) {
                    requiredMinimumVersionOfGradle = version.getVersion();
                }
            }
        }

        return Optional.ofNullable(requiredMinimumVersionOfGradle);
    }

    /**
     * A data class that holds information about a single variant which was a candidate for matching during resolution.
     *
     * This includes classifying its attributes into lists of compatible, incompatible, and absent attributes.
     */
    /* package */ static final class AssessedCandidate implements Describable {
        private final String name;
        // TODO: Turn this into a single multimap
        private final List<AssessedAttribute<?>> compatibleAttributes = new ArrayList<>();
        private final List<AssessedAttribute<?>> incompatibleAttributes = new ArrayList<>();
        private final List<AssessedAttribute<?>> onlyOnConsumer = new ArrayList<>();
        private final List<AssessedAttribute<?>> onlyOnProducer = new ArrayList<>();

        private AssessedCandidate(String name) {
            this.name = name;
        }

        public void addCompatible(AssessedAttribute<?> attribute) {
            if (notAlreadyInThere(attribute, compatibleAttributes)) {
                compatibleAttributes.add(attribute);
            }
        }

        private boolean notAlreadyInThere(AssessedAttribute<?> attribute, List<AssessedAttribute<?>> listToCheck) {
            return listToCheck.stream().noneMatch(e -> Objects.equals(e.getAttribute().getName(), attribute.getAttribute().getName()));
        }

        public void addIncompatible(AssessedAttribute<?> attribute) {
            if (notAlreadyInThere(attribute, incompatibleAttributes)) {
                incompatibleAttributes.add(attribute);
            }
        }

        public void addOnlyOnConsumer(AssessedAttribute<?> attribute) {
            if (notAlreadyInThere(attribute, onlyOnConsumer)) {
                onlyOnConsumer.add(attribute);
            }
        }

        public void addOnlyOnProducer(AssessedAttribute<?> attribute) {
            if (notAlreadyInThere(attribute, onlyOnProducer)) {
                onlyOnProducer.add(attribute);
            }
        }

        @Override
        public String getDisplayName() {
            return name;
        }

        public List<AssessedAttribute<?>> getCompatibleAttributes() {
            return compatibleAttributes;
        }

        public List<AssessedAttribute<?>> getIncompatibleAttributes() {
            return incompatibleAttributes;
        }

        // TODO: make these copies
        public List<AssessedAttribute<?>> getOnlyOnConsumerAttributes() {
            return onlyOnConsumer;
        }

        public List<AssessedAttribute<?>> getOnlyOnProducerAttributes() {
            return onlyOnProducer;
        }

        // TODO: MOVE THIS INTO FAILURE DESCRIBER
        boolean isPluginRequestUsingApiVersionAttribute(AttributeContainerInternal consumerAttributes) {
            return consumerAttributes.contains(GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE);
        }

        // TODO: Making this into a map would possibly make this faster
        // TODO: Also extract the name comparison into a method to reference
        public boolean alreadyAssessed(Attribute<Object> attribute) {
            return getCompatibleAttributes().stream().anyMatch(e -> Objects.equals(e.getAttribute().getName(), attribute.getName())) ||
                getIncompatibleAttributes().stream().anyMatch(e -> Objects.equals(e.getAttribute().getName(), attribute.getName())) ||
                getOnlyOnConsumerAttributes().stream().anyMatch(e -> Objects.equals(e.getAttribute().getName(), attribute.getName())) ||
                getOnlyOnProducerAttributes().stream().anyMatch(e -> Objects.equals(e.getAttribute().getName(), attribute.getName()));
        }
    }

    /**
     * A data class that records a single attribute, its requested value, and its provided value.
     */
    /* package */ static final class AssessedAttribute<T> {
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
