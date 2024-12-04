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

package org.gradle.internal.component.resolution.failure.describer;

import com.google.common.collect.Ordering;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.internal.component.resolution.failure.exception.ArtifactSelectionException;
import org.gradle.internal.component.resolution.failure.transform.SourceVariantData;
import org.gradle.internal.component.resolution.failure.transform.TransformationChainData;
import org.gradle.internal.component.resolution.failure.type.AmbiguousArtifactTransformsFailure;
import org.gradle.internal.logging.text.TreeFormatter;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * A {@link ResolutionFailureDescriber} that describes an {@link AmbiguousArtifactTransformsFailure}.
 */
public abstract class AmbiguousArtifactTransformsFailureDescriber extends AbstractResolutionFailureDescriber<AmbiguousArtifactTransformsFailure> {
    private static final String AMBIGUOUS_TRANSFORMATION_PREFIX = "Transformation failures are explained in more detail at ";
    private static final String AMBIGUOUS_TRANSFORMATION_SECTION = "sub:transform-ambiguity";

    @Override
    public ArtifactSelectionException describeFailure(AmbiguousArtifactTransformsFailure failure) {
        String message = buildFailureMsg(failure);
        List<String> resolutions = buildResolutions(suggestSpecificDocumentation(AMBIGUOUS_TRANSFORMATION_PREFIX, AMBIGUOUS_TRANSFORMATION_SECTION), suggestReviewAlgorithm());
        return new ArtifactSelectionException(message, failure, resolutions);
    }

    private String buildFailureMsg(AmbiguousArtifactTransformsFailure failure) {
        TreeFormatter formatter = new TreeFormatter(true);

        formatter.node("Found multiple transformation chains that produce a variant of '" + failure.describeRequestTarget() + "' with requested attributes");
        formatSortedAttributes(formatter, failure.getRequestedAttributes());
        formatter.node("Found the following transformation chains");

        Comparator<TransformationChainData> variantDataComparator =
            Comparator.comparing(TransformationChainData::summarizeTransformations)
                .thenComparing(x -> x.getFinalAttributes().toString());

        Map<SourceVariantData, List<TransformationChainData>> transformationPaths = failure.getPotentialVariants().stream()
            .collect(Collectors.groupingBy(
                TransformationChainData::getInitialVariant,
                () -> new TreeMap<>(Comparator.comparing(SourceVariantData::getVariantName)),
                Collectors.collectingAndThen(Collectors.toList(), list -> list.stream().sorted(variantDataComparator).collect(Collectors.toList()))));

        formatter.startChildren();
        transformationPaths.forEach((root, transformations) -> {
            formatSourceVariant(root, formatter);

            formatter.node("Candidate transformation chains");
            formatter.startChildren();
            transformations.forEach(transformationChainData -> {
                formatter.node("Transformation chain: " + transformationChainData.summarizeTransformations());
                formatter.startChildren();
                transformationChainData.getSteps().forEach(step -> {
                    formatter.node("'" + step.getTransformName() + "'");
                    formatter.startChildren();
                    formatter.node("Converts from attributes");
                    formatSortedAttributes(formatter, step.getFromAttributes());
                    formatter.node("To attributes");
                    formatSortedAttributes(formatter, step.getToAttributes());
                    formatter.endChildren();
                });
                formatter.endChildren();
            });
            formatter.endChildren();
            formatter.endChildren(); // End formatSourceVariant children
        });
        formatter.endChildren();

        return formatter.toString();
    }

    private void formatSourceVariant(SourceVariantData sourceVariantData, TreeFormatter formatter) {
        formatter.node("From " + sourceVariantData.getFormattedVariantName());
        formatter.startChildren();
        formatter.node("With source attributes");
        formatSortedAttributes(formatter, sourceVariantData.getAttributes());
    }

    private void formatSortedAttributes(TreeFormatter formatter, AttributeContainer attributes) {
        formatter.startChildren();
        for (Attribute<?> attribute : Ordering.usingToString().sortedCopy(attributes.keySet())) {
            formatter.node(attribute.getName() + " '" + attributes.getAttribute(attribute) + "'");
        }
        formatter.endChildren();
    }
}
