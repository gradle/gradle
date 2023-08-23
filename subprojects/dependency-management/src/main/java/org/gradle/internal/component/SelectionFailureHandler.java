/*
 * Copyright 2023 the original author or authors.
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

import com.google.common.collect.Ordering;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BrokenResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet;
import org.gradle.api.internal.artifacts.transform.AmbiguousTransformException;
import org.gradle.api.internal.artifacts.transform.TransformedVariant;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeDescriber;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.interfaces.ProblemGroup;
import org.gradle.api.problems.interfaces.Severity;
import org.gradle.internal.component.model.AttributeMatcher;
import org.gradle.internal.exceptions.StyledException;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.TreeFormatter;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.gradle.internal.component.AmbiguousConfigurationSelectionException.formatAttributeMatchesForIncompatibility;

/**
 * Provides a central location for logging and reporting variant selection failures appearing during
 * each stage of the selection process.
 *
 * All variant selection failures encountered during selection by the {@link org.gradle.internal.component.model.AttributeMatchingConfigurationSelector AttributeMatchingConfigurationSelector} or
 * {@link org.gradle.api.internal.artifacts.transform.AttributeMatchingVariantSelector AttributeMatchingVariantSelector}
 * should be routed through this class.
 *
 * This class reports failures to the {@link Problems} service.  It is a
 * Gradle managed type, and so it can serve as an injection point for any types wishing to be notified or respond
 * to the variant selection process.
 */
public class SelectionFailureHandler {
    private static final String FAILURE_TYPE = "Variant Selection Failure";
    private final Problems problemsService;

    public SelectionFailureHandler(Problems problemsService) {
        this.problemsService = problemsService;
    }

    public NoMatchingVariantSelectionException noMatchingVariantsSelectionFailure(AttributesSchema schema, String displayName, ImmutableAttributes componentRequested, List<? extends ResolvedVariant> variants, AttributeMatcher matcher, AttributeDescriber attributeDescriber) {
        String message = formatNoMatchingVariantsFailureMsg(displayName, componentRequested, variants, matcher, attributeDescriber);
        NoMatchingVariantSelectionException e = new NoMatchingVariantSelectionException(message);

        problemsService.createProblemBuilder()
            .label("No matching variants found")
            .undocumented()
            .noLocation()
            .type(FAILURE_TYPE)
            .group(ProblemGroup.GENERIC_ID)
            .severity(Severity.ERROR)
            .withException(e)
            .build();

        return e;
    }

    public AmbiguousVariantSelectionException ambiguousVariantSelectionFailure(AttributesSchema schema, String displayName, ImmutableAttributes componentRequested, List<? extends ResolvedVariant> matches, AttributeMatcher matcher, Set<ResolvedVariant> discarded, AttributeDescriber attributeDescriber) {
        String message = formatMultipleMatchingVariantsFailureMsg(attributeDescriber, displayName, componentRequested, matches, matcher, discarded);
        AmbiguousVariantSelectionException e = new AmbiguousVariantSelectionException(message);

        problemsService.createProblemBuilder()
            .label("Multiple matching variants found")
            .undocumented()
            .noLocation()
            .type(FAILURE_TYPE)
            .group(ProblemGroup.GENERIC_ID)
            .severity(Severity.ERROR)
            .withException(e)
            .build();

        return e;
    }

    public AmbiguousTransformException ambiguousTransformationFailure(AttributesSchema schema, String displayName, ImmutableAttributes componentRequested, List<TransformedVariant> transformedVariants) {
        String message = formatAmbiguousTransformMsg(displayName, componentRequested, transformedVariants);
        AmbiguousTransformException e = new AmbiguousTransformException(message);

        problemsService.createProblemBuilder()
            .label("Ambiguous artifact transformation")
            .undocumented()
            .noLocation()
            .type(FAILURE_TYPE)
            .group(ProblemGroup.GENERIC_ID)
            .severity(Severity.ERROR)
            .withException(e)
            .build();

        return e;
    }

    public BrokenResolvedArtifactSet unknownSelectionFailure(AttributesSchema schema, VariantSelectionException t) {
        problemsService.createProblemBuilder()
            .label("Variant selection failed")
            .undocumented()
            .noLocation()
            .type(FAILURE_TYPE)
            .group(ProblemGroup.GENERIC_ID)
            .severity(Severity.ERROR)
            .withException(t)
            .build();

        return new BrokenResolvedArtifactSet(t);
    }

    public BrokenResolvedArtifactSet unknownSelectionFailure(AttributesSchema schema, ResolvedVariantSet producer, Exception t) {
        return unknownSelectionFailure(schema, VariantSelectionException.selectionFailed(producer, t));
    }

    private String formatNoMatchingVariantsFailureMsg(String producerDisplayName,
                                                      AttributeContainerInternal consumer,
                                                      Collection<? extends ResolvedVariant> candidates,
                                                      AttributeMatcher matcher, AttributeDescriber describer) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node("No variants of " + StyledException.style(StyledTextOutput.Style.Info, producerDisplayName) + " match the consumer attributes");
        formatter.startChildren();
        for (ResolvedVariant variant : candidates) {
            formatter.node(variant.asDescribable().getCapitalizedDisplayName());
            formatAttributeMatchesForIncompatibility(formatter, consumer.asImmutable(), matcher, variant.getAttributes().asImmutable(), describer);
        }
        formatter.endChildren();
        return formatter.toString();
    }

    private String formatMultipleMatchingVariantsFailureMsg(AttributeDescriber describer, String producerDisplayName, AttributeContainerInternal consumer, List<? extends ResolvedVariant> variants, AttributeMatcher matcher, Set<ResolvedVariant> discarded) {
        TreeFormatter formatter = new TreeFormatter();
        if (consumer.getAttributes().isEmpty()) {
            formatter.node("More than one variant of " + producerDisplayName + " matches the consumer attributes");
        } else {
            formatter.node("The consumer was configured to find " + describer.describeAttributeSet(consumer.asMap()) + ". However we cannot choose between the following variants of " + producerDisplayName);
        }
        formatter.startChildren();
        for (ResolvedVariant variant : variants) {
            formatter.node(variant.asDescribable().getCapitalizedDisplayName());
            AmbiguousConfigurationSelectionException.formatAttributeMatchesForAmbiguity(formatter, consumer.asImmutable(), matcher, variant.getAttributes().asImmutable(), describer);
        }
        formatter.endChildren();
        if (!discarded.isEmpty()) {
            formatter.node("The following variants were also considered but didn't match the requested attributes:");
            formatter.startChildren();
            discarded.stream()
                .sorted(Comparator.comparing(v -> v.asDescribable().getCapitalizedDisplayName()))
                .forEach(discardedVariant -> {
                    formatter.node(discardedVariant.asDescribable().getCapitalizedDisplayName());
                    formatAttributeMatchesForIncompatibility(formatter, consumer.asImmutable(), matcher, discardedVariant.getAttributes().asImmutable(), describer);
                });
            formatter.endChildren();
        }
        return formatter.toString();
    }

    private String formatAmbiguousTransformMsg(String producerDisplayName, AttributeContainerInternal requested, List<TransformedVariant> candidates) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node("Found multiple transforms that can produce a variant of " + producerDisplayName + " with requested attributes");
        addSortedAttributes(formatter, requested);
        formatter.node("Found the following transforms");

        Comparator<TransformedVariant> variantComparator =
            Comparator.<TransformedVariant, String>comparing(x -> x.getTransformChain().getDisplayName())
                .thenComparing(x -> x.getAttributes().toString());
        Map<ResolvedVariant, List<TransformedVariant>> variantToTransforms = candidates.stream().collect(Collectors.groupingBy(
            TransformedVariant::getRoot,
            () -> new TreeMap<>(Comparator.comparing(variant -> variant.asDescribable().getDisplayName())),
            Collectors.collectingAndThen(Collectors.toList(), list -> list.stream().sorted(variantComparator).collect(Collectors.toList()))));

        formatter.startChildren();
        for (Map.Entry<ResolvedVariant, List<TransformedVariant>> entry : variantToTransforms.entrySet()) {
            formatter.node("From '" + entry.getKey().asDescribable().getDisplayName() + "'");
            formatter.startChildren();
            formatter.node("With source attributes");
            addSortedAttributes(formatter, entry.getKey().getAttributes());
            formatter.node("Candidate transform(s)");
            formatter.startChildren();
            for (TransformedVariant variant : entry.getValue()) {
                formatter.node("Transform '" + variant.getTransformChain().getDisplayName() + "' producing attributes:");
                addSortedAttributes(formatter, variant.getAttributes());
            }
            formatter.endChildren();
            formatter.endChildren();
        }
        formatter.endChildren();
        return formatter.toString();
    }

    private void addSortedAttributes(TreeFormatter formatter, AttributeContainer attributes) {
        formatter.startChildren();
        for (Attribute<?> attribute : Ordering.usingToString().sortedCopy(attributes.keySet())) {
            formatter.node(attribute.getName() + " '" + attributes.getAttribute(attribute) + "'");
        }
        formatter.endChildren();
    }
}
