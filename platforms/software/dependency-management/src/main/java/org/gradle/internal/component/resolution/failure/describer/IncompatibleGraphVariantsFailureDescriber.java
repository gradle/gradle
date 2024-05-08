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
    private static final String NO_VARIANTS_EXIST_PREFIX = "Creating consumable variants is explained in more detail at ";
    private static final String NO_VARIANTS_EXIST_SECTION = "sec:resolvable-consumable-configs";

    @Override
    public VariantSelectionException describeFailure(IncompatibleGraphVariantFailure failure, Optional<AttributesSchemaInternal> schema) {
        AttributeDescriber describer = AttributeDescriberSelector.selectDescriber(failure.getRequestedAttributes(), schema.orElseThrow(IllegalArgumentException::new));
        FailureSubType failureSubType = FailureSubType.determineFailureSubType(failure);
        String message = buildNoMatchingGraphVariantSelectionFailureMsg(new StyledAttributeDescriber(describer), failure, failureSubType);
        List<String> resolutions = buildResolutions(failureSubType);
        return new VariantSelectionException(message, failure, resolutions);
    }

    private String buildNoMatchingGraphVariantSelectionFailureMsg(StyledAttributeDescriber describer, IncompatibleGraphVariantFailure failure, FailureSubType failureSubType) {
        TreeFormatter formatter = new TreeFormatter();
        String targetVariantText = style(StyledTextOutput.Style.Info, failure.getRequestedName());
        if (failure.getRequestedAttributes().isEmpty()) {
            formatter.node("Unable to find a matching variant of " + targetVariantText);
        } else {
            formatter.node("No matching variant of " + targetVariantText + " was found. The consumer was configured to find " + describer.describeAttributeSet(failure.getRequestedAttributes().asMap()) + " but:");
        }

        formatter.startChildren();
        switch (failureSubType) {
            case NO_VARIANTS_EXIST:
                formatter.node("No variants exist.");
                break;
            case NO_VARIANTS_HAVE_ATTRIBUTES:
                formatter.node("None of the variants have attributes.");
                break;
            case NO_VARIANT_MATCHES_REQUESTED_ATTRIBUTES:
                // We're sorting the names of the configurations and later attributes
                // to make sure the output is consistently the same between invocations
                for (ResolutionCandidateAssessor.AssessedCandidate candidate : failure.getCandidates()) {
                    formatUnselectableVariant(candidate, formatter, describer);
                }
                break;
            default:
                throw new IllegalStateException("Unknown failure sub type: " + failureSubType);
        }
        formatter.endChildren();

        return formatter.toString();
    }

    private List<String> buildResolutions(FailureSubType failureSubType) {
        if (failureSubType == FailureSubType.NO_VARIANTS_EXIST) {
            String suggestReviewCreatingConsumableConfigs = NO_VARIANTS_EXIST_PREFIX + getDocumentationRegistry().getDocumentationFor("declaring_dependencies", NO_VARIANTS_EXIST_SECTION) + ".";
            return buildResolutions(suggestReviewCreatingConsumableConfigs, suggestReviewAlgorithm());
        } else {
            return buildResolutions(suggestSpecificDocumentation(NO_MATCHING_VARIANTS_PREFIX, NO_MATCHING_VARIANTS_SECTION), suggestReviewAlgorithm());
        }
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

    private enum FailureSubType {
        NO_VARIANTS_EXIST,
        NO_VARIANTS_HAVE_ATTRIBUTES,
        NO_VARIANT_MATCHES_REQUESTED_ATTRIBUTES;

        public static FailureSubType determineFailureSubType(IncompatibleGraphVariantFailure failure) {
            if (failure.getCandidates().isEmpty()) {
                return FailureSubType.NO_VARIANTS_EXIST;
            }
            if (failure.noCandidatesHaveAttributes()) {
                return FailureSubType.NO_VARIANTS_HAVE_ATTRIBUTES;
            } else {
                return FailureSubType.NO_VARIANT_MATCHES_REQUESTED_ATTRIBUTES;
            }
        }
    }
}
