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
import org.gradle.internal.component.resolution.failure.type.AmbiguousResolutionFailure;
import org.gradle.internal.logging.text.TreeFormatter;

import java.util.List;
import java.util.Optional;

/**
 * A {@link ResolutionFailureDescriber} that describes an {@link AmbiguousResolutionFailure}.
 */
public abstract class AmbiguousArtifactVariantsFailureDescriber extends AbstractResolutionFailureDescriber<AmbiguousResolutionFailure> {
    private static final String AMBIGUOUS_VARIANTS_PREFIX = "Ambiguity errors are explained in more detail at ";
    private static final String AMBIGUOUS_VARIANTS_SECTION = "sub:variant-ambiguity";

    @Override
    public ArtifactVariantSelectionException describeFailure(AmbiguousResolutionFailure failure, Optional<AttributesSchemaInternal> schema) {
        AttributeDescriber describer = AttributeDescriberSelector.selectDescriber(failure.getRequestedAttributes(), schema.orElseThrow(IllegalArgumentException::new));
        String message = buildMultipleMatchingVariantsFailureMsg(failure, describer);
        List<String> resolutions = buildResolutions(suggestSpecificDocumentation(AMBIGUOUS_VARIANTS_PREFIX, AMBIGUOUS_VARIANTS_SECTION), suggestReviewAlgorithm());
        return new ArtifactVariantSelectionException(message, failure, resolutions);
    }

    private String buildMultipleMatchingVariantsFailureMsg(AmbiguousResolutionFailure failure, AttributeDescriber describer) {
        TreeFormatter formatter = new TreeFormatter();
        if (failure.getRequestedAttributes().isEmpty()) {
            formatter.node("More than one variant of " + failure.getRequestedName() + " matches the consumer attributes");
        } else {
            formatter.node("The consumer was configured to find " + describer.describeAttributeSet(failure.getRequestedAttributes().asMap()) + ". However we cannot choose between the following variants of " + failure.getRequestedName());
        }
        formatter.startChildren();
        for (AssessedCandidate assessedCandidate : failure.getCandidates()) {
            String candidateName = assessedCandidate.getDisplayName();
            formatter.node(candidateName);
            formatAttributeMatchesForAmbiguity(assessedCandidate, formatter, describer);
        }
        formatter.endChildren();
        return formatter.toString();
    }
}
