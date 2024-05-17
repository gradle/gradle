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
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor.AssessedCandidate;
import org.gradle.internal.component.resolution.failure.exception.ArtifactVariantSelectionException;
import org.gradle.internal.component.resolution.failure.type.IncompatibleResolutionFailure;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.TreeFormatter;

import java.util.List;
import java.util.Optional;

import static org.gradle.internal.exceptions.StyledException.style;

/**
 * A {@link ResolutionFailureDescriber} that describes an {@link IncompatibleResolutionFailure}.
 */
public abstract class IncompatibleArtifactVariantsFailureDescriber extends AbstractResolutionFailureDescriber<IncompatibleResolutionFailure> {
    private static final String NO_MATCHING_VARIANTS_PREFIX = "No matching variant errors are explained in more detail at ";
    private static final String NO_MATCHING_VARIANTS_SECTION = "sub:variant-no-match";

    @Override
    public ArtifactVariantSelectionException describeFailure(IncompatibleResolutionFailure failure, Optional<AttributesSchemaInternal> schema) {
        String message = buildIncompatibleArtifactVariantsFailureMsg(failure, schema.orElseThrow(IllegalArgumentException::new));
        List<String> resolutions = buildResolutions(suggestSpecificDocumentation(NO_MATCHING_VARIANTS_PREFIX, NO_MATCHING_VARIANTS_SECTION), suggestReviewAlgorithm());
        return new ArtifactVariantSelectionException(message, failure, resolutions);
    }

    private String buildIncompatibleArtifactVariantsFailureMsg(IncompatibleResolutionFailure failure, AttributesSchemaInternal schema) {
        AttributeDescriber describer = AttributeDescriberSelector.selectDescriber(failure.getRequestedAttributes(), schema);
        TreeFormatter formatter = new TreeFormatter();
        formatter.node("No variants of " + style(StyledTextOutput.Style.Info, failure.getRequestedName()) + " match the consumer attributes");
        formatter.startChildren();
        for (AssessedCandidate assessedCandidate : failure.getCandidates()) {
            formatter.node(assessedCandidate.getDisplayName());
            formatAttributeMatchesForIncompatibility(assessedCandidate, formatter, describer);
        }
        formatter.endChildren();
        return formatter.toString();
    }
}
