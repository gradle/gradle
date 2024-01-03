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
 * A utility class used by {@link ResolutionFailureHandler} to examine failures for specific scenarios and to generate human-readable error messages.
 *
 * It does this by categorizing attributes for a variant request into lists of compatible, incompatible, and other.  These categorized lists can
 * be used for messaging, or to assess whether a particular failure scenario occurred.
 */
/* package */ final class ResolutionAssessor {
    public List<VariantGraphResolveMetadata> extractVariantsConsideredForSelection(GraphSelectionCandidates candidates) {
        boolean variantAware = candidates.isUseVariants();
        final List<VariantGraphResolveMetadata> variants;
        if (variantAware) {
            variants = new ArrayList<>(candidates.getVariants().size());
            for (VariantGraphResolveState variant : candidates.getVariants()) {
                variants.add(variant.getMetadata());
            }
        } else {
            variants = new ArrayList<>(candidates.getCandidateConfigurations());
        }
        variants.sort(Comparator.comparing(VariantGraphResolveMetadata::getName));
        return variants;
    }

    public List<AssessedCandidate> assessCandidates(List<VariantGraphResolveMetadata> variantMetadatas, AttributeContainerInternal consumerAttributes, AttributeMatcher attributeMatcher) {
        return variantMetadatas.stream()
            .map(variantMetadata -> assessCandidate(variantMetadata.getName(), variantMetadata.getAttributes().asImmutable(), consumerAttributes.asImmutable(), attributeMatcher))
            .collect(Collectors.toList());
    }

    public AssessedCandidate assessCandidate(
        String name,
        ImmutableAttributes consumerAttributes,
        ImmutableAttributes candidateAttributes,
        AttributeMatcher attributeMatcher
    ) {
        // Must assess consumer first, to get proper attribute type if its present (it will just be string on the Producer)
        AssessedCandidate assessedCandidate = new AssessedCandidate(name);
        consumerAttributes.getAttributes().keySet().forEach(attribute -> assessAttribute(consumerAttributes, candidateAttributes, attributeMatcher, attribute, assessedCandidate));
        candidateAttributes.getAttributes().keySet().forEach(attribute -> assessAttribute(consumerAttributes, candidateAttributes, attributeMatcher, attribute, assessedCandidate));
        return assessedCandidate;
    }

    private static void assessAttribute(ImmutableAttributes immutableConsumer, ImmutableAttributes immutableProducer, AttributeMatcher attributeMatcher, Attribute<?> attribute, AssessedCandidate assessedCandidate) {
        Attribute<Object> untyped = Cast.uncheckedCast(attribute);

        String attributeName = Objects.requireNonNull(attribute).getName();
        AttributeValue<?> consumerValue = immutableConsumer.findEntry(attributeName);
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

    public boolean isPluginRequestUsingApiVersionAttribute(AttributeContainerInternal consumerAttributes) {
        return consumerAttributes.contains(GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE);
    }

    public Optional<String> findHigherRequiredVersionOfGradle(AttributeContainerInternal consumerAttributes, AttributeMatcher attributeMatcher, GraphSelectionCandidates candidates) {
        List<VariantGraphResolveMetadata> candidateMetadatas = extractVariantsConsideredForSelection(candidates);
        List<AssessedCandidate> categorizedAttributes = assessCandidates(candidateMetadatas, consumerAttributes, attributeMatcher);
        return findIncompatibleRequiredMinimumVersionOfGradle(categorizedAttributes);
    }

    private Optional<String> findIncompatibleRequiredMinimumVersionOfGradle(List<AssessedCandidate> candidates) {
        String requiredMinimumVersionOfGradle = null;

        for (AssessedCandidate candidate : candidates) {
            Optional<GradleVersion> providedVersion = candidate.getIncompatibleAttributes().stream()
                .filter(incompatibleAttribute -> incompatibleAttribute.getAttribute().getName().equals(GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE.getName()))
                .map(apiVersionAttribute -> GradleVersion.version((String) Objects.requireNonNull(apiVersionAttribute.getProvided())))
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
    }
}
