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
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor.AssessedAttribute;
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor.AssessedCandidate;
import org.gradle.internal.component.resolution.failure.type.ResolutionFailure;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.TreeFormatter;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.gradle.internal.exceptions.StyledException.style;

/**
 * An abstract base class for implementing {@link ResolutionFailureDescriber}s.
 *
 * This type provides methods for suggesting common resolutions, and doing common formatting
 * of failure messages.
 *
 * @param <EXCEPTION> The type of exception that this describer will produce
 * @param <FAILURE> The type of {@link ResolutionFailure} that this describer can describe
 */
public abstract class AbstractResolutionFailureDescriber<EXCEPTION extends AbstractVariantSelectionException, FAILURE extends ResolutionFailure> implements ResolutionFailureDescriber<EXCEPTION, FAILURE> {
    private static final String DEFAULT_MESSAGE_PREFIX = "Review the variant matching algorithm at ";

    @Inject
    protected abstract DocumentationRegistry getDocumentationRegistry();

    protected void suggestReviewAlgorithm(AbstractVariantSelectionException exception) {
        exception.addResolution(DEFAULT_MESSAGE_PREFIX + getDocumentationRegistry().getDocumentationFor("variant_attributes", "sec:abm_algorithm") + ".");
    }

    protected void suggestSpecificDocumentation(AbstractVariantSelectionException exception, String prefix, String section) {
        exception.addResolution(prefix + getDocumentationRegistry().getDocumentationFor("variant_model", section) + ".");
    }

    protected void formatAttributeSection(TreeFormatter formatter, String section, List<String> values) {
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

    protected void formatAttributeMatchesForAmbiguity(
        AssessedCandidate assessedCandidate,
        TreeFormatter formatter,
        AttributeDescriber describer
    ) {
        // None of the nullability warnings are relevant here because the attribute values are only retrieved from collections that will contain them
        @SuppressWarnings("DataFlowIssue") Map<Attribute<?>, ?> compatibleAttrs = assessedCandidate.getCompatibleAttributes().stream()
            .collect(Collectors.toMap(AssessedAttribute::getAttribute, AssessedAttribute::getProvided, (a, b) -> a));
        @SuppressWarnings("DataFlowIssue") List<String> onlyOnProducer = assessedCandidate.getOnlyOnCandidateAttributes().stream()
            .map(assessedAttribute -> "Provides " + describer.describeExtraAttribute(assessedAttribute.getAttribute(), assessedAttribute.getProvided()) + " but the consumer didn't ask for it")
            .collect(Collectors.toList());
        @SuppressWarnings("DataFlowIssue") List<String> onlyOnConsumer = assessedCandidate.getOnlyOnRequestAttributes().stream()
            .map(assessedAttribute -> "Doesn't say anything about " + describer.describeMissingAttribute(assessedAttribute.getAttribute(), assessedAttribute.getRequested()))
            .collect(Collectors.toList());

        List<String> other = new ArrayList<>(onlyOnProducer.size() + onlyOnConsumer.size());
        other.addAll(onlyOnProducer);
        other.addAll(onlyOnConsumer);
        other.sort(String::compareTo);

        if (!compatibleAttrs.isEmpty()) {
            formatter.append(" declares ").append(style(StyledTextOutput.Style.SuccessHeader, describer.describeAttributeSet(compatibleAttrs)));
        }
        formatter.startChildren();
        formatAttributeSection(formatter, "Unmatched attribute", other);
        formatter.endChildren();
    }

    protected void formatAttributeMatchesForIncompatibility(
        AssessedCandidate assessedCandidate,
        TreeFormatter formatter,
        AttributeDescriber describer
    ) {
        // None of the nullability warnings are relevant here because the attribute values are only retrieved from collections that will contain them
        @SuppressWarnings("DataFlowIssue") Map<Attribute<?>, ?> compatibleAttrs = assessedCandidate.getCompatibleAttributes().stream()
            .collect(Collectors.toMap(AssessedAttribute::getAttribute, AssessedAttribute::getProvided, (a, b) -> a));
        @SuppressWarnings("DataFlowIssue") Map<Attribute<?>, ?> incompatibleAttrs = assessedCandidate.getIncompatibleAttributes().stream()
            .collect(Collectors.toMap(AssessedAttribute::getAttribute, AssessedAttribute::getProvided, (a, b) -> a));
        @SuppressWarnings("DataFlowIssue") Map<Attribute<?>, ?> incompatibleConsumerAttrs = assessedCandidate.getIncompatibleAttributes().stream()
            .collect(Collectors.toMap(AssessedAttribute::getAttribute, AssessedAttribute::getRequested, (a, b) -> a));
        @SuppressWarnings("DataFlowIssue") List<String> otherValues = assessedCandidate.getOnlyOnRequestAttributes().stream()
            .map(assessedAttribute -> "Doesn't say anything about " + describer.describeMissingAttribute(assessedAttribute.getAttribute(), assessedAttribute.getRequested()))
            .sorted()
            .collect(Collectors.toList());

        if (!compatibleAttrs.isEmpty()) {
            formatter.append(" declares ").append(style(StyledTextOutput.Style.SuccessHeader, describer.describeAttributeSet(compatibleAttrs)));
        }
        formatter.startChildren();
        if (!incompatibleAttrs.isEmpty()) {
            formatter.node("Incompatible because this component declares " + style(StyledTextOutput.Style.FailureHeader, describer.describeAttributeSet(incompatibleAttrs)) + " and the consumer needed " + style(StyledTextOutput.Style.FailureHeader, describer.describeAttributeSet(incompatibleConsumerAttrs)));
        }
        formatAttributeSection(formatter, "Other compatible attribute", otherValues);
        formatter.endChildren();
    }
}
