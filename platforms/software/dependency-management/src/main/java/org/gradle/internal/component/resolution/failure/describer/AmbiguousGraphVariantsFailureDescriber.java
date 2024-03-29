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

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.attributes.AttributeDescriber;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.internal.component.model.AttributeDescriberSelector;
import org.gradle.internal.component.resolution.failure.CapabilitiesDescriber;
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor;
import org.gradle.internal.component.resolution.failure.exception.VariantSelectionException;
import org.gradle.internal.component.resolution.failure.type.VariantAwareAmbiguousResolutionFailure;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.TreeFormatter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.gradle.internal.exceptions.StyledException.style;

/**
 * A {@link ResolutionFailureDescriber} that describes an {@link VariantAwareAmbiguousResolutionFailure}.
 */
public abstract class AmbiguousGraphVariantsFailureDescriber extends AbstractResolutionFailureDescriber<VariantAwareAmbiguousResolutionFailure> {
    private static final String AMBIGUOUS_VARIANTS_PREFIX = "Ambiguity errors are explained in more detail at ";
    private static final String AMBIGUOUS_VARIANTS_SECTION = "sub:variant-ambiguity";

    @Override
    public VariantSelectionException describeFailure(VariantAwareAmbiguousResolutionFailure failure, Optional<AttributesSchemaInternal> schema) {
        String message = buildAmbiguousGraphVariantsFailureMsg(failure, schema.orElseThrow(IllegalArgumentException::new));
        List<String> resolutions = buildResolutions(suggestSpecificDocumentation(AMBIGUOUS_VARIANTS_PREFIX, AMBIGUOUS_VARIANTS_SECTION), suggestReviewAlgorithm());
        return new VariantSelectionException(message, failure, resolutions);
    }

    private String buildAmbiguousGraphVariantsFailureMsg(VariantAwareAmbiguousResolutionFailure failure, AttributesSchemaInternal schema) {
        AttributeDescriber describer = AttributeDescriberSelector.selectDescriber(failure.getRequestedAttributes(), schema);

        Map<String, ResolutionCandidateAssessor.AssessedCandidate> ambiguousVariants = new TreeMap<>();
        for (ResolutionCandidateAssessor.AssessedCandidate candidate : failure.getCandidates()) {
            ambiguousVariants.put(candidate.getDisplayName(), candidate);
        }
        TreeFormatter formatter = new TreeFormatter();
       if (failure.getRequestedAttributes().isEmpty()) {
            formatter.node("Cannot choose between the following variants of ");
        } else {
            formatter.node("The consumer was configured to find " + describer.describeAttributeSet(failure.getRequestedAttributes().asMap()) + ". However we cannot choose between the following variants of ");
        }
        formatter.append(style(StyledTextOutput.Style.Info, failure.getRequestedName()));
        formatter.startChildren();
        for (String configuration : ambiguousVariants.keySet()) {
            formatter.node(configuration);
        }
        formatter.endChildren();
        formatter.node("All of them match the consumer attributes");
        // We're sorting the names of the variants and later attributes
        // to make sure the output is consistently the same between invocations
        formatter.startChildren();
        for (ResolutionCandidateAssessor.AssessedCandidate assessedCandidate : ambiguousVariants.values()) {
            formatUnselectable(assessedCandidate, formatter, failure.getTargetComponentId(), describer);
        }
        formatter.endChildren();

        return formatter.toString();
    }

    private void formatUnselectable(
        ResolutionCandidateAssessor.AssessedCandidate assessedCandidate,
        TreeFormatter formatter,
        ModuleVersionIdentifier targetComponentId,
        AttributeDescriber describer
    ) {
        formatter.node("Variant '");
        formatter.append(assessedCandidate.getDisplayName());
        formatter.append("'");
        formatter.append(" " + CapabilitiesDescriber.describeCapabilitiesWithTitle(targetComponentId, assessedCandidate.getCandidateCapabilities().asSet()));

        formatAttributeMatchesForAmbiguity(assessedCandidate, formatter, describer);
    }
}
