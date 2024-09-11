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
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor.AssessedCandidate;
import org.gradle.internal.component.resolution.failure.exception.VariantSelectionByNameException;
import org.gradle.internal.component.resolution.failure.type.ConfigurationNotCompatibleFailure;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.TreeFormatter;

import javax.inject.Inject;
import java.util.List;

import static org.gradle.internal.exceptions.StyledException.style;

/**
 * A {@link ResolutionFailureDescriber} that describes an {@link ConfigurationNotCompatibleFailure}.
 */
public abstract class ConfigurationNotCompatibleFailureDescriber extends AbstractResolutionFailureDescriber<ConfigurationNotCompatibleFailure> {
    private static final String INCOMPATIBLE_VARIANTS_PREFIX = "Incompatible variant errors are explained in more detail at ";
    private static final String INCOMPATIBLE_VARIANTS_SECTION = "sub:variant-incompatible";

    private final AttributeDescriberRegistry attributeDescribers;

    @Inject
    public ConfigurationNotCompatibleFailureDescriber(
        AttributeDescriberRegistry attributeDescribers
    ) {
        this.attributeDescribers = attributeDescribers;
    }

    @Override
    public VariantSelectionByNameException describeFailure(ConfigurationNotCompatibleFailure failure) {
        AttributeDescriber describer = AttributeDescriberSelector.selectDescriber(failure.getRequestedAttributes(), attributeDescribers.getDescribers());
        String message = buildFailureMsg(failure, describer);
        List<String> resolutions = buildResolutions(suggestSpecificDocumentation(INCOMPATIBLE_VARIANTS_PREFIX, INCOMPATIBLE_VARIANTS_SECTION), suggestReviewAlgorithm());
        return new VariantSelectionByNameException(message, failure, resolutions);
    }

    private String buildFailureMsg(
        ConfigurationNotCompatibleFailure failure,
        AttributeDescriber describer
    ) {
        ResolutionCandidateAssessor.AssessedCandidate assessedCandidate = failure.getCandidates().get(0);
        TreeFormatter formatter = new TreeFormatter();
        String candidateName = assessedCandidate.getDisplayName();
        formatter.node("Configuration '" + candidateName + "' in " + style(StyledTextOutput.Style.Info, failure.getTargetComponent().getDisplayName()) + " does not match the consumer attributes");
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
