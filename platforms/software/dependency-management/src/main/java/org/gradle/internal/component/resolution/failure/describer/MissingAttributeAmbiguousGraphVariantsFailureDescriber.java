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
import org.gradle.api.internal.attributes.AttributeDescriber;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.internal.component.model.AttributeDescriberSelector;
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor;
import org.gradle.internal.component.resolution.failure.type.VariantAwareAmbiguousResolutionFailure;
import org.gradle.internal.logging.text.TreeFormatter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A {@link ResolutionFailureDescriber} that describes a {@link VariantAwareAmbiguousResolutionFailure} where
 * there is a single differing attribute between all available variants that is missing from the request.
 * <p>
 * In this situation, we can provide a very brief message pointing to the exact solution needed.
 */
public abstract class MissingAttributeAmbiguousGraphVariantsFailureDescriber extends AmbiguousGraphVariantsFailureDescriber {
    /**
     * Map from failure -> name of attribute that would distinguish each candidate.
     * This map exists to avoid re-discovering the unrequested attributes between the calls to `canDescribeFailure` and `describeFailure`.
     * Each failure will be added once (by identity), then removed during failure description.
     */
    private final IdentityHashMap<VariantAwareAmbiguousResolutionFailure, String> suggestableDistinctAttributes = new IdentityHashMap<>();

    @Override
    public boolean canDescribeFailure(VariantAwareAmbiguousResolutionFailure failure) {
        // Map from name of attribute -> set of attribute names for each candidate
        Map<String, Set<String>> unrequestedAttributesWithValues = new HashMap<>();

        failure.getCandidates().forEach(candidate -> {
            candidate.getOnlyOnCandidateAttributes().forEach(candidateAttribute -> {
                Attribute<?> attribute = candidateAttribute.getAttribute();
                Set<String> unrequestedValuesForAttribute = unrequestedAttributesWithValues.computeIfAbsent(attribute.getName(), name -> new HashSet<>());
                unrequestedValuesForAttribute.add(Objects.requireNonNull(candidateAttribute.getProvided()).toString());
            });
        });

        // List of map entries where there is a distinct attribute value for every available candidate
        List<Map.Entry<String, Set<String>>> attributesDistinctlyIdentifyingCandidates = unrequestedAttributesWithValues.entrySet().stream()
            .filter(entry -> entry.getValue().size() == failure.getCandidates().size())
            .collect(Collectors.toList());

        if (attributesDistinctlyIdentifyingCandidates.size() == 1) {
            suggestableDistinctAttributes.put(failure, attributesDistinctlyIdentifyingCandidates.get(0).getKey());
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected String buildAmbiguousGraphVariantsFailureMsg(VariantAwareAmbiguousResolutionFailure failure, AttributesSchemaInternal schema) {
        String distinguishingAttribute = suggestableDistinctAttributes.remove(failure);
        assert distinguishingAttribute != null;

        AttributeDescriber describer = AttributeDescriberSelector.selectDescriber(failure.getRequestedAttributes(), schema);
        TreeFormatter formatter = new TreeFormatter();
        summarizeAmbiguousVariants(failure, describer, formatter, false);
        buildSpecificAttributeSuggestionMsg(failure, distinguishingAttribute, formatter);
        return formatter.toString();
    }

    @Override
    protected List<String> buildResolutions(String... resolutions) {
        List<String> result = new ArrayList<>(super.buildResolutions(resolutions));
        result.add(suggestDependencyInsight());
        return result;
    }

    private void buildSpecificAttributeSuggestionMsg(VariantAwareAmbiguousResolutionFailure failure, String distinguishingAttribute, TreeFormatter formatter) {
        formatter.node("The only attribute distinguishing these variants is '" + distinguishingAttribute + "'. Add this attribute to the consumer's configuration to resolve the ambiguity:");
        formatter.startChildren();
        failure.getCandidates().forEach(candidate -> formatter.node("Value: '" + attributeValueForCandidate(candidate, distinguishingAttribute) + "' selects variant: '" + candidate.getDisplayName() + "'"));
        formatter.endChildren();
    }

    private String attributeValueForCandidate(ResolutionCandidateAssessor.AssessedCandidate candidate, String distinguishingAttribute) {
        return candidate.getOnlyOnCandidateAttributes().stream()
            .filter(attribute -> Objects.equals(attribute.getAttribute().getName(), distinguishingAttribute))
            .map(assessedAttribute -> Objects.requireNonNull(assessedAttribute.getProvided()).toString())
            .findFirst().orElseThrow(IllegalStateException::new);
    }

    private String suggestDependencyInsight() {
        return "Use the dependencyInsight report with the --all-variants option to view all variants of the ambiguous dependency.  This report is described at " + getDocumentationRegistry().getDocumentationFor("viewing_debugging_dependencies", "sec:identifying_reason_dependency_selection") + ".";
    }
}
