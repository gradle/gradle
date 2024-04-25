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
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.internal.component.model.AttributeDescriberSelector;
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor;
import org.gradle.internal.component.resolution.failure.StyledAttributeDescriber;
import org.gradle.internal.component.resolution.failure.exception.VariantSelectionException;
import org.gradle.internal.component.resolution.failure.type.IncompatibleGraphVariantFailure;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.TreeFormatter;

import java.util.List;
import java.util.Optional;

import static org.gradle.internal.exceptions.StyledException.style;

/**
 * A {@link ResolutionFailureDescriber} that describes an {@link IncompatibleGraphVariantFailure}.
 */
public abstract class IncompatibleGraphVariantsFailureDescriber extends AbstractResolutionFailureDescriber<IncompatibleGraphVariantFailure> {
    private static final String NO_MATCHING_VARIANTS_PREFIX = "No matching variant errors are explained in more detail at ";
    private static final String NO_MATCHING_VARIANTS_SECTION = "sub:variant-no-match";

    @Override
    public VariantSelectionException describeFailure(IncompatibleGraphVariantFailure failure, Optional<AttributesSchemaInternal> schema) {
        AttributeDescriber describer = AttributeDescriberSelector.selectDescriber(failure.getRequestedAttributes(), schema.orElseThrow(IllegalArgumentException::new));
        String message = buildNoMatchingGraphVariantSelectionFailureMsg(new StyledAttributeDescriber(describer), failure);
        List<String> resolutions = buildResolutions(suggestSpecificDocumentation(NO_MATCHING_VARIANTS_PREFIX, NO_MATCHING_VARIANTS_SECTION), suggestReviewAlgorithm());
        return new VariantSelectionException(message, failure, resolutions);
    }

    protected String buildNoMatchingGraphVariantSelectionFailureMsg(StyledAttributeDescriber describer, IncompatibleGraphVariantFailure failure) {
        TreeFormatter formatter = new TreeFormatter();
        String targetVariantText = style(StyledTextOutput.Style.Info, failure.getRequestedName());
        if (failure.getRequestedAttributes().isEmpty()) {
            formatter.node("Unable to find a matching variant of " + targetVariantText);
        } else {
            formatter.node("No matching variant of " + targetVariantText + " was found. The consumer was configured to find " + describer.describeAttributeSet(failure.getRequestedAttributes().asMap()) + " but:");
        }
        formatter.startChildren();
        if (failure.getCandidates().size() < 1) {
            formatter.node("No variants were found.");
        } else {
            if (failure.noCandidatesHaveAttributes()) {
                formatter.node("None of the variants have attributes.");
            } else {
                // We're sorting the names of the configurations and later attributes
                // to make sure the output is consistently the same between invocations
                for (ResolutionCandidateAssessor.AssessedCandidate candidate : failure.getCandidates()) {
                    formatUnselectableVariant(candidate, formatter, describer);
                }
            }
        }
        formatter.endChildren();
        return formatter.toString();
    }

    private void formatUnselectableVariant(
        ResolutionCandidateAssessor.AssessedCandidate assessedCandidate,
        TreeFormatter formatter,
        AttributeDescriber describer
    ) {
        formatter.node("Variant '");
        formatter.append(assessedCandidate.getDisplayName());
        formatter.append("'");
        formatAttributeMatchesForIncompatibility(assessedCandidate, formatter, describer);
    }
}
