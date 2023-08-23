/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeDescriber;
import org.gradle.api.internal.attributes.AttributeValue;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Cast;
import org.gradle.internal.component.model.AttributeMatcher;
import org.gradle.internal.component.model.ComponentGraphResolveMetadata;
import org.gradle.internal.component.model.VariantGraphResolveMetadata;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.TreeFormatter;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class AmbiguousConfigurationSelectionException extends AbstractConfigurationSelectionException {
    public AmbiguousConfigurationSelectionException(AttributeDescriber describer, AttributeContainerInternal fromConfigurationAttributes,
                                                    AttributeMatcher attributeMatcher,
                                                    List<? extends VariantGraphResolveState> matches,
                                                    ComponentGraphResolveMetadata targetComponent,
                                                    boolean variantAware,
                                                    Set<VariantGraphResolveState> discarded) {
        super(generateMessage(new StyledDescriber(describer), fromConfigurationAttributes, attributeMatcher, matches, discarded, targetComponent, variantAware));
    }

    private static String generateMessage(AttributeDescriber describer, AttributeContainerInternal fromConfigurationAttributes, AttributeMatcher attributeMatcher, List<? extends VariantGraphResolveState> matches, Set<VariantGraphResolveState> discarded, ComponentGraphResolveMetadata targetComponent, boolean variantAware) {
        Map<String, VariantGraphResolveState> ambiguousVariants = new TreeMap<>();
        for (VariantGraphResolveState match : matches) {
            ambiguousVariants.put(match.getName(), match);
        }
        TreeFormatter formatter = new TreeFormatter();
        String configTerm = variantAware ? "variants" : "configurations";
        if (fromConfigurationAttributes.isEmpty()) {
            formatter.node("Cannot choose between the following " + configTerm + " of ");
        } else {
            formatter.node("The consumer was configured to find " + describer.describeAttributeSet(fromConfigurationAttributes.asMap()) + ". However we cannot choose between the following " + configTerm + " of ");
        }
        formatter.append(style(StyledTextOutput.Style.Info, targetComponent.getId().getDisplayName()));
        formatter.startChildren();
        for (String configuration : ambiguousVariants.keySet()) {
            formatter.node(configuration);
        }
        formatter.endChildren();
        formatter.node("All of them match the consumer attributes");
        // We're sorting the names of the variants and later attributes
        // to make sure the output is consistently the same between invocations
        formatter.startChildren();
        for (VariantGraphResolveState ambiguousVariant : ambiguousVariants.values()) {
            formatConfiguration(formatter, targetComponent, fromConfigurationAttributes, attributeMatcher, ambiguousVariant.getMetadata(), variantAware, true, describer);
        }
        formatter.endChildren();
        if (!discarded.isEmpty()) {
            formatter.node("The following " + configTerm + " were also considered but didn't match the requested attributes:");
            formatter.startChildren();
            discarded.stream()
                .sorted(Comparator.comparing(VariantGraphResolveState::getName))
                .forEach(discardedConf -> formatConfiguration(formatter, targetComponent, fromConfigurationAttributes, attributeMatcher, discardedConf.getMetadata(), variantAware, false, describer));
            formatter.endChildren();
        }

        return formatter.toString();
    }

    static void formatConfiguration(TreeFormatter formatter,
                                    ComponentGraphResolveMetadata targetComponent,
                                    AttributeContainerInternal consumerAttributes,
                                    AttributeMatcher attributeMatcher,
                                    VariantGraphResolveMetadata variant,
                                    boolean variantAware,
                                    boolean ambiguous,
                                    AttributeDescriber describer) {
        AttributeContainerInternal producerAttributes = variant.getAttributes();
        if (variantAware) {
            formatter.node("Variant '");
        } else {
            formatter.node("Configuration '");
        }
        formatter.append(variant.getName());
        formatter.append("'");
        if (variantAware) {
            formatter.append(" " + CapabilitiesSupport.prettifyCapabilities(targetComponent, variant.getCapabilities().getCapabilities()));
        }
        if (ambiguous) {
            formatAttributeMatchesForAmbiguity(formatter, consumerAttributes.asImmutable(), attributeMatcher, producerAttributes.asImmutable(), describer);
        } else {
            formatAttributeMatchesForIncompatibility(formatter, consumerAttributes.asImmutable(), attributeMatcher, producerAttributes.asImmutable(), describer);
        }
    }

    static void formatAttributeMatchesForIncompatibility(TreeFormatter formatter,
                                                         ImmutableAttributes immutableConsumer,
                                                         AttributeMatcher attributeMatcher,
                                                         ImmutableAttributes immutableProducer,
                                                         AttributeDescriber describer) {
        Map<String, Attribute<?>> allAttributes = collectAttributes(immutableConsumer, immutableProducer);
        List<String> otherValues = Lists.newArrayListWithExpectedSize(allAttributes.size());
        Map<Attribute<?>, ?> compatibleAttrs = Maps.newLinkedHashMap();
        Map<Attribute<?>, ?> incompatibleAttrs = Maps.newLinkedHashMap();
        Map<Attribute<?>, ?> incompatibleConsumerAttrs = Maps.newLinkedHashMap();
        for (Attribute<?> attribute : allAttributes.values()) {
            Attribute<Object> untyped = Cast.uncheckedCast(attribute);
            String attributeName = attribute.getName();
            AttributeValue<Object> consumerValue = immutableConsumer.findEntry(untyped);
            AttributeValue<?> producerValue = immutableProducer.findEntry(attributeName);
            if (consumerValue.isPresent() && producerValue.isPresent()) {
                if (attributeMatcher.isMatching(untyped, producerValue.coerce(attribute), consumerValue.coerce(attribute))) {
                    compatibleAttrs.put(attribute, Cast.uncheckedCast(producerValue.get()));
                } else {
                    incompatibleAttrs.put(attribute, Cast.uncheckedCast(producerValue.get()));
                    incompatibleConsumerAttrs.put(attribute, Cast.uncheckedCast(consumerValue.get()));
                }
            } else if (consumerValue.isPresent()) {
                otherValues.add("Doesn't say anything about " + describer.describeMissingAttribute(attribute, consumerValue.get()));
            }
        }
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

    static void formatAttributeMatchesForAmbiguity(TreeFormatter formatter,
                                                   ImmutableAttributes immutableConsumer,
                                                   AttributeMatcher attributeMatcher,
                                                   ImmutableAttributes immutableProducer,
                                                   AttributeDescriber describer) {
        Map<String, Attribute<?>> allAttributes = collectAttributes(immutableConsumer, immutableProducer);
        Map<Attribute<?>, ?> compatibleAttrs = Maps.newLinkedHashMap();
        List<String> otherValues = Lists.newArrayListWithExpectedSize(allAttributes.size());
        for (Attribute<?> attribute : allAttributes.values()) {
            Attribute<Object> untyped = Cast.uncheckedCast(attribute);
            String attributeName = attribute.getName();
            AttributeValue<Object> consumerValue = immutableConsumer.findEntry(untyped);
            AttributeValue<?> producerValue = immutableProducer.findEntry(attributeName);
            if (consumerValue.isPresent() && producerValue.isPresent()) {
                if (attributeMatcher.isMatching(untyped, producerValue.coerce(attribute), consumerValue.coerce(attribute))) {
                    compatibleAttrs.put(attribute, Cast.uncheckedCast(producerValue.get()));
                }
            } else if (consumerValue.isPresent()) {
                otherValues.add("Doesn't say anything about " + describer.describeMissingAttribute(attribute, consumerValue.get()));
            } else {
                otherValues.add("Provides " + describer.describeExtraAttribute(attribute, producerValue.get()) + " but the consumer didn't ask for it");
            }
        }
        if (!compatibleAttrs.isEmpty()) {
            formatter.append(" declares ").append(style(StyledTextOutput.Style.SuccessHeader, describer.describeAttributeSet(compatibleAttrs)));
        }
        formatter.startChildren();
        formatAttributeSection(formatter, "Unmatched attribute", otherValues);
        formatter.endChildren();
    }

    private static Map<String, Attribute<?>> collectAttributes(ImmutableAttributes consumerAttributes, ImmutableAttributes producerAttributes) {
        Map<String, Attribute<?>> allAttributes = new TreeMap<>();
        for (Attribute<?> attribute : producerAttributes.keySet()) {
            allAttributes.put(attribute.getName(), attribute);
        }
        for (Attribute<?> attribute : consumerAttributes.keySet()) {
            allAttributes.put(attribute.getName(), attribute);
        }
        return allAttributes;
    }

    private static void formatAttributeSection(TreeFormatter formatter, String section, List<String> values) {
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
