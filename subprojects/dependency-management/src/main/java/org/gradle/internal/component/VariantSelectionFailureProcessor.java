/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.component;

import com.google.common.collect.Ordering;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BrokenResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet;
import org.gradle.api.internal.artifacts.transform.AmbiguousTransformException;
import org.gradle.api.internal.artifacts.transform.TransformedVariant;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeDescriber;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.model.AttributeMatcher;
import org.gradle.internal.exceptions.StyledException;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.TreeFormatter;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.gradle.internal.component.AmbiguousConfigurationSelectionException.formatAttributeMatchesForIncompatibility;

public class VariantSelectionFailureProcessor {
    public VariantSelectionFailureProcessor() {}

    public AmbiguousTransformException ambiguousTransformationFailure(AttributesSchema schema, String displayName, ImmutableAttributes componentRequested, List<TransformedVariant> transformedVariants) {
        String message = formatAmbiguousTransformMsg(displayName, componentRequested, transformedVariants);
        List<String> interpretations = interpretFailure(schema, displayName, componentRequested, transformedVariants);
        return new AmbiguousTransformException(addIntepretations(interpretations, message));
    }

    public NoMatchingVariantSelectionException noMatchingVariantsSelectionFailure(AttributesSchema schema, String displayName, ImmutableAttributes componentRequested, List<? extends ResolvedVariant> variants, AttributeMatcher matcher, AttributeDescriber attributeDescriber) {
        String message = formatNoMatchingVariantsFailureMsg(displayName, componentRequested, variants, matcher, attributeDescriber);
        List<String> interpretations = interpretFailure(schema, displayName, componentRequested, variants);
        return new NoMatchingVariantSelectionException(addIntepretations(interpretations, message));
    }

    public AmbiguousVariantSelectionException ambiguousVariantSelectionFailure(AttributesSchema schema, String displayName, ImmutableAttributes componentRequested, List<? extends HasAttributes> matches, AttributeMatcher matcher, Set<ResolvedVariant> discarded) {
        String message = "";
        List<String> interpretations = interpretFailure(schema, displayName, componentRequested, matches);
        return new AmbiguousVariantSelectionException(addIntepretations(interpretations, message));
    }

    public BrokenResolvedArtifactSet unknownSelectionFailure(AttributesSchema schema, VariantSelectionException t) {
        return new BrokenResolvedArtifactSet(t);
    }

    public BrokenResolvedArtifactSet unknownSelectionFailure(AttributesSchema schema, ResolvedVariantSet producer, Exception t) {
        return new BrokenResolvedArtifactSet(VariantSelectionException.selectionFailed(producer, t));
    }

    private static String formatNoMatchingVariantsFailureMsg(String producerDisplayName,
                                                             AttributeContainerInternal consumer,
                                                             Collection<? extends ResolvedVariant> candidates,
                                                             AttributeMatcher matcher, AttributeDescriber describer) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node("No variants of " + StyledException.style(StyledTextOutput.Style.Info, producerDisplayName) + " match the consumer attributes");
        formatter.startChildren();
        for (ResolvedVariant variant : candidates) {
            formatter.node(variant.asDescribable().getCapitalizedDisplayName());
            formatAttributeMatchesForIncompatibility(formatter, consumer.asImmutable(), matcher, variant.getAttributes().asImmutable(), describer);
        }
        formatter.endChildren();
        return formatter.toString();
    }

    private String formatAmbiguousTransformMsg(String producerDisplayName, AttributeContainerInternal requested, List<TransformedVariant> candidates) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node("Found multiple transforms that can produce a variant of " + producerDisplayName + " with requested attributes");
        formatAttributes(formatter, requested);
        formatter.node("Found the following transforms");

        Comparator<TransformedVariant> variantComparator =
            Comparator.<TransformedVariant, String>comparing(x -> x.getTransformChain().getDisplayName())
                .thenComparing(x -> x.getAttributes().toString());
        Map<ResolvedVariant, List<TransformedVariant>> variantToTransforms = candidates.stream().collect(Collectors.groupingBy(
            TransformedVariant::getRoot,
            () -> new TreeMap<>(Comparator.comparing(variant -> variant.asDescribable().getDisplayName())),
            Collectors.collectingAndThen(Collectors.toList(), list -> list.stream().sorted(variantComparator).collect(Collectors.toList()))));

        formatter.startChildren();
        for (Map.Entry<ResolvedVariant, List<TransformedVariant>> entry : variantToTransforms.entrySet()) {
            formatter.node("From '" + entry.getKey().asDescribable().getDisplayName() + "'");
            formatter.startChildren();
            formatter.node("With source attributes");
            formatAttributes(formatter, entry.getKey().getAttributes());
            formatter.node("Candidate transform(s)");
            formatter.startChildren();
            for (TransformedVariant variant : entry.getValue()) {
                formatter.node("Transform '" + variant.getTransformChain().getDisplayName() + "' producing attributes:");
                formatAttributes(formatter, variant.getAttributes());
            }
            formatter.endChildren();
            formatter.endChildren();
        }
        formatter.endChildren();
        return formatter.toString();
    }

    public void formatAttributes(TreeFormatter formatter, AttributeContainer attributes) {
        formatter.startChildren();
        for (Attribute<?> attribute : Ordering.usingToString().sortedCopy(attributes.keySet())) {
            formatter.node(attribute.getName() + " '" + attributes.getAttribute(attribute) + "'");
        }
        formatter.endChildren();
    }

    private List<String> interpretFailure(AttributesSchema schema, String displayName, ImmutableAttributes componentRequested, List<? extends HasAttributes> transformedVariants) {
        return schema.getVariantSelectionListeners().get().stream()
            .map(listener -> listener.onFailure(displayName, componentRequested, transformedVariants))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }

    private static String addIntepretations(List<String> interpretations, String message) {
        return String.join("\n", interpretations) + message;
    }
}
