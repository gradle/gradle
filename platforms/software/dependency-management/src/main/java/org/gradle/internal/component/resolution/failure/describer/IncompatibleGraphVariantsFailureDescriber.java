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
import org.gradle.internal.component.NoMatchingGraphVariantsException;
import org.gradle.internal.component.model.AttributeDescriberSelector;
import org.gradle.internal.component.model.ComponentGraphResolveMetadata;
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor;
import org.gradle.internal.component.resolution.failure.StyledAttributeDescriber;
import org.gradle.internal.component.resolution.failure.type.IncompatibleGraphVariantFailure;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.TreeFormatter;

import static org.gradle.internal.exceptions.StyledException.style;

/**
 * A {@link ResolutionFailureDescriber} that describes an {@link IncompatibleGraphVariantFailure}.
 */
public abstract class IncompatibleGraphVariantsFailureDescriber extends AbstractResolutionFailureDescriber<NoMatchingGraphVariantsException, IncompatibleGraphVariantFailure> {
    private static final String NO_MATCHING_VARIANTS_PREFIX = "No matching variant errors are explained in more detail at ";
    private static final String NO_MATCHING_VARIANTS_SECTION = "sub:variant-no-match";

    @Override
    public NoMatchingGraphVariantsException describeFailure(IncompatibleGraphVariantFailure failure) {
        AttributeDescriber describer = AttributeDescriberSelector.selectDescriber(failure.getRequestedAttributes(), failure.getSchema());
        String message = buildNoMatchingGraphVariantSelectionFailureMsg(new StyledAttributeDescriber(describer), failure);
        NoMatchingGraphVariantsException e = new NoMatchingGraphVariantsException(message);
        suggestReviewAlgorithm(e);
        suggestSpecificDocumentation(e, NO_MATCHING_VARIANTS_PREFIX, NO_MATCHING_VARIANTS_SECTION);
        return e;
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
        if (failure.noCandidatesHaveAttributes()) {
            formatter.node("None of the variants have attributes.");
        } else {
            // We're sorting the names of the configurations and later attributes
            // to make sure the output is consistently the same between invocations
            for (ResolutionCandidateAssessor.AssessedCandidate candidate : failure.getCandidates()) {
                formatUnselectableVariant(candidate, formatter, failure.getTargetComponent(), describer);
            }
        }
        formatter.endChildren();
        return formatter.toString();
    }

    private void formatUnselectableVariant(
        ResolutionCandidateAssessor.AssessedCandidate assessedCandidate,
        TreeFormatter formatter,
        ComponentGraphResolveMetadata targetComponent,
        AttributeDescriber describer
    ) {
        formatter.node("Variant '");
        formatter.append(assessedCandidate.getDisplayName());
        formatter.append("'");
        formatAttributeMatchesForIncompatibility(assessedCandidate, formatter, describer);
    }
}
