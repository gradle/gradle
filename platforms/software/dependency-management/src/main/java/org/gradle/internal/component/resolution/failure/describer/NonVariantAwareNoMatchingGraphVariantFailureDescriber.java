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

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.attributes.AttributeDescriber;
import org.gradle.internal.component.model.ComponentGraphResolveMetadata;
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor;
import org.gradle.internal.component.resolution.failure.ResolutionFailure;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.TreeFormatter;

import static org.gradle.internal.exceptions.StyledException.style;

public final class NonVariantAwareNoMatchingGraphVariantFailureDescriber extends AbstractNoMatchingGraphVariantFailureDescriber {
    public NonVariantAwareNoMatchingGraphVariantFailureDescriber(DocumentationRegistry documentationRegistry) {
        super(documentationRegistry);
    }

    @Override
    public boolean canDescribeFailure(ResolutionFailure failure) {
        return failure.getType() == ResolutionFailure.ResolutionFailureType.NO_MATCHING_VARIANTS && !failure.isVariantAware();
    }

    @Override
    protected String buildNoMatchingGraphVariantSelectionFailureMsg(StyledAttributeDescriber describer, ResolutionFailure failure) {
        TreeFormatter formatter = new TreeFormatter();
        String targetVariantText = style(StyledTextOutput.Style.Info, failure.getRequestedName());
        if (failure.getRequestedAttributes().isEmpty()) {
            formatter.node("Unable to find a matching configuration of " + targetVariantText);
        } else {
            formatter.node("No matching configuration of " + targetVariantText + " was found. The consumer was configured to find " + describer.describeAttributeSet(failure.getRequestedAttributes().asMap()) + " but:");
        }
        formatter.startChildren();
        if (failure.noCandidatesHaveAttributes()) {
            formatter.node("None of the consumable configurations have attributes.");
        } else {
            // We're sorting the names of the configurations and later attributes
            // to make sure the output is consistently the same between invocations
            for (ResolutionCandidateAssessor.AssessedCandidate candidate : failure.getCandidates()) {
                formatUnselectableConfiguration(candidate, formatter, failure.getTargetComponent(), describer);
            }
        }
        formatter.endChildren();
        return formatter.toString();
    }

    private void formatUnselectableConfiguration(
        ResolutionCandidateAssessor.AssessedCandidate assessedCandidate,
        TreeFormatter formatter,
        ComponentGraphResolveMetadata targetComponent,
        AttributeDescriber describer
    ) {
        formatter.node("Configuration '");
        formatter.append(assessedCandidate.getDisplayName());
        formatter.append("'");
        formatAttributeMatchesForIncompatibility(assessedCandidate, formatter, describer);
    }
}
