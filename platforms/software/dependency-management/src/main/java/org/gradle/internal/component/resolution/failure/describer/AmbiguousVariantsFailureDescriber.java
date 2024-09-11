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

import org.gradle.api.internal.attributes.AttributeDescriber;
import org.gradle.api.internal.attributes.AttributeDescriberRegistry;
import org.gradle.internal.component.model.AttributeDescriberSelector;
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor;
import org.gradle.internal.component.resolution.failure.exception.VariantSelectionByAttributesException;
import org.gradle.internal.component.resolution.failure.formatting.CapabilitiesDescriber;
import org.gradle.internal.component.resolution.failure.type.AmbiguousVariantsFailure;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.TreeFormatter;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.gradle.internal.exceptions.StyledException.style;

/**
 * A {@link ResolutionFailureDescriber} that describes a generic {@link AmbiguousVariantsFailure}.
 */
public abstract class AmbiguousVariantsFailureDescriber extends AbstractResolutionFailureDescriber<AmbiguousVariantsFailure> {
    private static final String AMBIGUOUS_VARIANTS_PREFIX = "Ambiguity errors are explained in more detail at ";
    private static final String AMBIGUOUS_VARIANTS_SECTION = "sub:variant-ambiguity";

    private final AttributeDescriberRegistry attributeDescribers;

    @Inject
    public AmbiguousVariantsFailureDescriber(
        AttributeDescriberRegistry attributeDescribers
    ) {
        this.attributeDescribers = attributeDescribers;
    }

    @Override
    public VariantSelectionByAttributesException describeFailure(AmbiguousVariantsFailure failure) {
        String message = buildFailureMsg(failure, attributeDescribers.getDescribers());
        List<String> resolutions = buildResolutions(suggestSpecificDocumentation(AMBIGUOUS_VARIANTS_PREFIX, AMBIGUOUS_VARIANTS_SECTION), suggestReviewAlgorithm());
        return new VariantSelectionByAttributesException(message, failure, resolutions);
    }

    protected String buildFailureMsg(AmbiguousVariantsFailure failure, List<AttributeDescriber> attributeDescribers) {
        AttributeDescriber describer = AttributeDescriberSelector.selectDescriber(failure.getRequestedAttributes(), attributeDescribers);
        TreeFormatter formatter = new TreeFormatter();
        Map<String, ResolutionCandidateAssessor.AssessedCandidate> ambiguousVariants = summarizeAmbiguousVariants(failure, describer, formatter, true);

        // We're sorting the names of the variants and later attributes
        // to make sure the output is consistently the same between invocations
        formatter.startChildren();
        for (ResolutionCandidateAssessor.AssessedCandidate assessedCandidate : ambiguousVariants.values()) {
            formatUnselectable(assessedCandidate, formatter, describer);
        }
        formatter.endChildren();

        return formatter.toString();
    }

    protected Map<String, ResolutionCandidateAssessor.AssessedCandidate> summarizeAmbiguousVariants(AmbiguousVariantsFailure failure, AttributeDescriber describer, TreeFormatter formatter, boolean listAvailableVariants) {
        Map<String, ResolutionCandidateAssessor.AssessedCandidate> ambiguousVariants = new TreeMap<>();
        for (ResolutionCandidateAssessor.AssessedCandidate candidate : failure.getCandidates()) {
            ambiguousVariants.put(candidate.getDisplayName(), candidate);
        }
        if (failure.getRequestedAttributes().isEmpty()) {
            formatter.node("Cannot choose between the available variants of ");
        } else {
            String node = "The consumer was configured to find " + describer.describeAttributeSet(failure.getRequestedAttributes().asMap());
            if (listAvailableVariants) {
                node = node + ". However we cannot choose between the following variants of ";
            } else {
                node = node + ". There are several available matching variants of ";
            }
            formatter.node(node);
        }
        formatter.append(style(StyledTextOutput.Style.Info, failure.describeRequestTarget()));
        if (listAvailableVariants) {
            formatter.startChildren();
            for (String configuration : ambiguousVariants.keySet()) {
                formatter.node(configuration);
            }
            formatter.endChildren();
            formatter.node("All of them match the consumer attributes");
        }
        return ambiguousVariants;
    }

    private void formatUnselectable(
        ResolutionCandidateAssessor.AssessedCandidate assessedCandidate,
        TreeFormatter formatter,
        AttributeDescriber describer
    ) {
        formatter.node("Variant '");
        formatter.append(assessedCandidate.getDisplayName());
        formatter.append("'");
        formatter.append(" " + CapabilitiesDescriber.describeCapabilitiesWithTitle(assessedCandidate.getCandidateCapabilities().asSet()));

        formatAttributeMatchesForAmbiguity(assessedCandidate, formatter, describer);
    }
}
