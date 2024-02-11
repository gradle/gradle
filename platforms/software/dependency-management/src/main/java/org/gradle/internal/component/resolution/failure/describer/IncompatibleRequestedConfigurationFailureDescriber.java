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
import org.gradle.internal.component.IncompatibleGraphVariantsException;
import org.gradle.internal.component.model.AttributeDescriberSelector;
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor;
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor.AssessedCandidate;
import org.gradle.internal.component.resolution.failure.type.IncompatibleRequestedConfigurationFailure;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.TreeFormatter;

import static org.gradle.internal.exceptions.StyledException.style;

/**
 * A {@link ResolutionFailureDescriber} that describes an {@link IncompatibleRequestedConfigurationFailure}.
 */
public abstract class IncompatibleRequestedConfigurationFailureDescriber extends AbstractResolutionFailureDescriber<IncompatibleGraphVariantsException, IncompatibleRequestedConfigurationFailure> {
    private static final String INCOMPATIBLE_VARIANTS_PREFIX = "Incompatible variant errors are explained in more detail at ";
    private static final String INCOMPATIBLE_VARIANTS_SECTION = "sub:variant-incompatible";

    @Override
    public IncompatibleGraphVariantsException describeFailure(IncompatibleRequestedConfigurationFailure failure) {
        AttributeDescriber describer = AttributeDescriberSelector.selectDescriber(failure.getRequestedAttributes(), failure.getSchema());
        String message = buildIncompatibleGraphVariantsFailureMsg(failure, describer);
        IncompatibleGraphVariantsException e = new IncompatibleGraphVariantsException(message);
        e.addResolution(INCOMPATIBLE_VARIANTS_PREFIX + getDocumentationRegistry().getDocumentationFor("variant_model", INCOMPATIBLE_VARIANTS_SECTION) + ".");
        suggestReviewAlgorithm(e);
        return e;
    }

    private String buildIncompatibleGraphVariantsFailureMsg(
        IncompatibleRequestedConfigurationFailure failure,
        AttributeDescriber describer
    ) {
        ResolutionCandidateAssessor.AssessedCandidate assessedCandidate = failure.getCandidates().get(0);
        TreeFormatter formatter = new TreeFormatter();
        String candidateName = assessedCandidate.getDisplayName();
        formatter.node("Configuration '" + candidateName + "' in " + style(StyledTextOutput.Style.Info, failure.getRequestedName()) + " does not match the consumer attributes");
        formatUnselectable(assessedCandidate, formatter, describer);
        return formatter.toString();
    }

    private void formatUnselectable(
        AssessedCandidate assessedCandidate,
        TreeFormatter formatter,
        AttributeDescriber describer
    ) {
        formatter.node("Configuration '");
        formatter.append(assessedCandidate.getDisplayName());
        formatter.append("'");

        formatAttributeMatchesForIncompatibility(assessedCandidate, formatter, describer);
    }
}
