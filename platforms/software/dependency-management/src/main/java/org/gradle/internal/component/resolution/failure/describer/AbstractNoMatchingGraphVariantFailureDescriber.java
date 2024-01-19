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

import org.gradle.api.attributes.Attribute;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.attributes.AttributeDescriber;
import org.gradle.internal.component.AbstractVariantSelectionException;
import org.gradle.internal.component.NoMatchingGraphVariantsException;
import org.gradle.internal.component.model.AttributeDescriberSelector;
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor;
import org.gradle.internal.component.resolution.failure.ResolutionFailure;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.TreeFormatter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.gradle.internal.component.ResolutionFailureHandler.DEFAULT_MESSAGE_PREFIX;
import static org.gradle.internal.exceptions.StyledException.style;

/* package */ abstract class AbstractNoMatchingGraphVariantFailureDescriber implements ResolutionFailureDescriber<NoMatchingGraphVariantsException> {
    protected static final String NO_MATCHING_VARIANTS_PREFIX = "No matching variant errors are explained in more detail at ";
    protected static final String NO_MATCHING_VARIANTS_SECTION = "sub:variant-no-match";

    protected final DocumentationRegistry documentationRegistry;

    protected AbstractNoMatchingGraphVariantFailureDescriber(DocumentationRegistry documentationRegistry) {
        this.documentationRegistry = documentationRegistry;
    }

    @Override
    public NoMatchingGraphVariantsException describeFailure(ResolutionFailure failure) {
        AttributeDescriber describer = AttributeDescriberSelector.selectDescriber(failure.getRequestedAttributes(), failure.getSchema());
        String message = buildNoMatchingGraphVariantSelectionFailureMsg(new StyledAttributeDescriber(describer), failure);
        NoMatchingGraphVariantsException e = new NoMatchingGraphVariantsException(message);
        suggestReviewAlgorithm(e);
        e.addResolution(NO_MATCHING_VARIANTS_PREFIX + documentationRegistry.getDocumentationFor("variant_model", NO_MATCHING_VARIANTS_SECTION + "."));
        return e;
    }

    protected abstract String buildNoMatchingGraphVariantSelectionFailureMsg(StyledAttributeDescriber describer, ResolutionFailure failure);

    private void suggestReviewAlgorithm(AbstractVariantSelectionException exception) {
        exception.addResolution(DEFAULT_MESSAGE_PREFIX + documentationRegistry.getDocumentationFor("variant_attributes", "sec:abm_algorithm") + ".");
    }

    protected void formatAttributeMatchesForIncompatibility(
        ResolutionCandidateAssessor.AssessedCandidate assessedCandidate,
        TreeFormatter formatter,
        AttributeDescriber describer
    ) {
        // None of the nullability warnings are relevant here because the attribute values are only retrieved from collections that will contain them
        Map<Attribute<?>, ?> compatibleAttrs = assessedCandidate.getCompatibleAttributes().stream()
            .collect(Collectors.toMap(ResolutionCandidateAssessor.AssessedAttribute::getAttribute, ResolutionCandidateAssessor.AssessedAttribute::getProvided, (a, b) -> a));
        Map<Attribute<?>, ?> incompatibleAttrs = assessedCandidate.getIncompatibleAttributes().stream()
            .collect(Collectors.toMap(ResolutionCandidateAssessor.AssessedAttribute::getAttribute, ResolutionCandidateAssessor.AssessedAttribute::getProvided, (a, b) -> a));
        Map<Attribute<?>, ?> incompatibleConsumerAttrs = assessedCandidate.getIncompatibleAttributes().stream()
            .collect(Collectors.toMap(ResolutionCandidateAssessor.AssessedAttribute::getAttribute, ResolutionCandidateAssessor.AssessedAttribute::getRequested, (a, b) -> a));
        List<String> otherValues = assessedCandidate.getOnlyOnRequestAttributes().stream()
            .map(assessedAttribute -> "Doesn't say anything about " + describer.describeMissingAttribute(assessedAttribute.getAttribute(), assessedAttribute.getRequested()))
            .sorted()
            .collect(Collectors.toList());

        if (!compatibleAttrs.isEmpty()) {
            formatter.append(" declares ").append(style(StyledTextOutput.Style.SuccessHeader, describer.describeAttributeSet(compatibleAttrs)));
        }
        formatter.startChildren();
        if (!incompatibleAttrs.isEmpty()) {
            formatter.node("Incompatible because this component declares " + style(StyledTextOutput.Style.FailureHeader, describer.describeAttributeSet(incompatibleAttrs)) + " and the consumer needed <FailureHeader>" + describer.describeAttributeSet(incompatibleConsumerAttrs) + "</FailureHeader>");
        }
        formatAttributeSection(formatter, "Other compatible attribute", otherValues);
        formatter.endChildren();
    }

    private void formatAttributeSection(TreeFormatter formatter, String section, List<String> values) {
        if (!values.isEmpty()) {
            if (values.size() > 1) {
                formatter.node(section + "s");
            } else {
                formatter.node(section);
            }
            formatter.startChildren();
            values.forEach(formatter::node);
            formatter.endChildren();
        }
    }
}
