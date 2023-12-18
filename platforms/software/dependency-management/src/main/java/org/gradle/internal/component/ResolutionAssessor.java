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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

    @Nonnull
    public List<AssessedCandidate> assessCandidates(AttributeContainerInternal consumerAttributes, AttributeMatcher attributeMatcher, List<VariantGraphResolveMetadata> variants) {
        return variants.stream()
            .map(variant -> assessCandidates(variant, consumerAttributes.asImmutable(), attributeMatcher, variant.getAttributes()))
            .collect(Collectors.toList());
    }

    private AssessedCandidate assessCandidates(
        VariantGraphResolveMetadata variantMetadata,
        ImmutableAttributes immutableConsumer,
        AttributeMatcher attributeMatcher,
        ImmutableAttributes immutableProducer
    ) {
        AssessedCandidate assessedCandidate = new AssessedCandidate(variantMetadata);

        Map<String, Attribute<?>> allAttributes = ImmutableAttributes.mapOfAll(immutableConsumer, immutableProducer);
        for (Attribute<?> attribute : allAttributes.values()) {
            Attribute<Object> untyped = Cast.uncheckedCast(attribute);

            String attributeName = Objects.requireNonNull(attribute).getName();
            AttributeValue<Object> consumerValue = immutableConsumer.findEntry(untyped);
            AttributeValue<?> producerValue = immutableProducer.findEntry(attributeName);

            if (consumerValue.isPresent() && producerValue.isPresent()) {
                if (attributeMatcher.isMatching(untyped, producerValue.coerce(attribute), consumerValue.coerce(attribute))) {
                    assessedCandidate.addCompatible(new AssessedAttribute<>(attribute, Cast.uncheckedCast(consumerValue.get()), Cast.uncheckedCast(producerValue.get())));
                } else {
                    assessedCandidate.addIncompatible(new AssessedAttribute<>(attribute, Cast.uncheckedCast(consumerValue.get()), Cast.uncheckedCast(producerValue.get())));
                }
            } else if (consumerValue.isPresent()) {
                assessedCandidate.addOther(new AssessedAttribute<>(attribute, Cast.uncheckedCast(consumerValue.get()), null));
            }
        }

        return assessedCandidate;
    }

    public boolean isPluginRequestUsingApiVersionAttribute(AttributeContainerInternal consumerAttributes) {
        return consumerAttributes.contains(GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE);
    }

    public Optional<String> findHigherRequiredVersionOfGradle(AttributeContainerInternal consumerAttributes, AttributeMatcher attributeMatcher, GraphSelectionCandidates candidates) {
        List<VariantGraphResolveMetadata> variants = extractVariantsConsideredForSelection(candidates);
        List<AssessedCandidate> categorizedAttributes = assessCandidates(consumerAttributes, attributeMatcher, variants);
        return findIncompatibleRequiredMinimumVersionOfGradle(categorizedAttributes);
    }

    private Optional<String> findIncompatibleRequiredMinimumVersionOfGradle(List<AssessedCandidate> variantsWithCategorizedAttributes) {
        String requiredMinimumVersionOfGradle = null;

        for (AssessedCandidate assessedCandidate : variantsWithCategorizedAttributes) {
            Optional<GradleVersion> providedVersion = assessedCandidate.getIncompatibleAttributes().stream()
                .filter(incompatibleAttribute -> incompatibleAttribute.getAttribute().equals(GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE))
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
     * This includes classifying its attributes into lists of compatible, incompatible, and other attributes.
     */
    /* package */ static final class AssessedCandidate {
        private final VariantGraphResolveMetadata variantMetadata;
        private final List<AssessedAttribute<?>> compatibleAttributes = new ArrayList<>();
        private final List<AssessedAttribute<?>> incompatibleAttributes = new ArrayList<>();
        private final List<AssessedAttribute<?>> otherAttributes = new ArrayList<>();

        private AssessedCandidate(VariantGraphResolveMetadata variantMetadata) {
            this.variantMetadata = variantMetadata;
        }

        public void addCompatible(AssessedAttribute<?> attribute) {
            compatibleAttributes.add(attribute);
        }

        public void addIncompatible(AssessedAttribute<?> attribute) {
            incompatibleAttributes.add(attribute);
        }

        public void addOther(AssessedAttribute<?> attribute) {
            otherAttributes.add(attribute);
        }

        public VariantGraphResolveMetadata getVariantMetadata() {
            return variantMetadata;
        }

        public List<AssessedAttribute<?>> getCompatibleAttributes() {
            return compatibleAttributes;
        }

        public List<AssessedAttribute<?>> getIncompatibleAttributes() {
            return incompatibleAttributes;
        }

        public List<AssessedAttribute<?>> getOtherAttributes() {
            return otherAttributes;
        }

        boolean isPluginRequestUsingApiVersionAttribute(AttributeContainerInternal consumerAttributes) {
            return consumerAttributes.contains(GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE);
        }
    }

    /**
     * A data class that records a single attribute, its requested value, and its provided value.
     */
    /* package */ static final class AssessedAttribute<T> {
        private final Attribute<T> attribute;
        private final T requested;
        @Nullable
        private final T provided;

        private AssessedAttribute(Attribute<T> attribute, T requested, @Nullable T provided) {
            this.attribute = attribute;
            this.requested = requested;
            this.provided = provided;
        }

        public Attribute<T> getAttribute() {
            return attribute;
        }

        public T getRequested() {
            return requested;
        }

        @Nullable
        public T getProvided() {
            return provided;
        }
    }
}
