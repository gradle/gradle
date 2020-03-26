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
import org.gradle.api.attributes.Attribute;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeValue;
import org.gradle.api.internal.attributes.ConsumerAttributeDescriber;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Cast;
import org.gradle.internal.component.model.AttributeMatcher;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.logging.text.TreeFormatter;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class AmbiguousConfigurationSelectionException extends RuntimeException {
    public AmbiguousConfigurationSelectionException(ConsumerAttributeDescriber describer, AttributeContainerInternal fromConfigurationAttributes,
                                                    AttributeMatcher attributeMatcher,
                                                    List<? extends ConfigurationMetadata> matches,
                                                    ComponentResolveMetadata targetComponent,
                                                    boolean variantAware,
                                                    Set<ConfigurationMetadata> discarded) {
        super(generateMessage(describer, fromConfigurationAttributes, attributeMatcher, matches, discarded, targetComponent, variantAware));
    }

    private static String generateMessage(ConsumerAttributeDescriber describer, AttributeContainerInternal fromConfigurationAttributes, AttributeMatcher attributeMatcher, List<? extends ConfigurationMetadata> matches, Set<ConfigurationMetadata> discarded, ComponentResolveMetadata targetComponent, boolean variantAware) {
        Map<String, ConfigurationMetadata> ambiguousConfigurations = new TreeMap<String, ConfigurationMetadata>();
        for (ConfigurationMetadata match : matches) {
            ambiguousConfigurations.put(match.getName(), match);
        }
        TreeFormatter formatter = new TreeFormatter();
        String configTerm = variantAware ? "variants" : "configurations";
        if (fromConfigurationAttributes.isEmpty()) {
            formatter.node("Cannot choose between the following " + configTerm + " of ");
        } else {
            formatter.node("The consumer was configured to find " + describer.describe(fromConfigurationAttributes) + ". However we cannot choose between the following " + configTerm + " of ");
        }
        formatter.append(targetComponent.getId().getDisplayName());
        formatter.startChildren();
        for (String configuration : ambiguousConfigurations.keySet()) {
            formatter.node(configuration);
        }
        formatter.endChildren();
        formatter.node("All of them match the consumer attributes");
        // We're sorting the names of the configurations and later attributes
        // to make sure the output is consistently the same between invocations
        formatter.startChildren();
        for (ConfigurationMetadata ambiguousConf : ambiguousConfigurations.values()) {
            formatConfiguration(formatter, targetComponent, fromConfigurationAttributes, attributeMatcher, ambiguousConf, variantAware, true);
        }
        formatter.endChildren();
        if (!discarded.isEmpty()) {
            formatter.node("The following " + configTerm + " were also considered but didn't match the requested attributes:");
            formatter.startChildren();
            discarded.stream()
                .sorted(Comparator.comparing(ConfigurationMetadata::getName))
                .forEach(discardedConf -> {
                    formatConfiguration(formatter, targetComponent, fromConfigurationAttributes, attributeMatcher, discardedConf, variantAware, false);
                });
            formatter.endChildren();
        }

        return formatter.toString();
    }

    static void formatConfiguration(TreeFormatter formatter, ComponentResolveMetadata targetComponent, AttributeContainerInternal consumerAttributes, AttributeMatcher attributeMatcher, ConfigurationMetadata configuration, boolean variantAware, boolean ambiguous) {
        AttributeContainerInternal producerAttributes = configuration.getAttributes();
        if (variantAware) {
            formatter.node("Variant '");
        } else {
            formatter.node("Configuration '");
        }
        formatter.append(configuration.getName());
        formatter.append("'");
        if (variantAware) {
            formatter.append(" " + CapabilitiesSupport.prettifyCapabilities(targetComponent, configuration.getCapabilities().getCapabilities()));
        }
        if (ambiguous) {
            formatAttributeMatchesForAmbiguity(formatter, consumerAttributes.asImmutable(), attributeMatcher, producerAttributes.asImmutable());
        } else {
            formatAttributeMatchesForIncompatibility(formatter, consumerAttributes.asImmutable(), attributeMatcher, producerAttributes.asImmutable());
        }
    }

    static void formatAttributeMatchesForIncompatibility(TreeFormatter formatter, ImmutableAttributes immutableConsumer, AttributeMatcher attributeMatcher, ImmutableAttributes immutableProducer) {
        Map<String, Attribute<?>> allAttributes = collectAttributes(immutableConsumer, immutableProducer);
        formatter.startChildren();
        List<String> incompatibleValues = Lists.newArrayListWithExpectedSize(allAttributes.size());
        List<String> otherValues = Lists.newArrayListWithExpectedSize(allAttributes.size());
        for (Attribute<?> attribute : allAttributes.values()) {
            Attribute<Object> untyped = Cast.uncheckedCast(attribute);
            String attributeName = attribute.getName();
            AttributeValue<Object> consumerValue = immutableConsumer.findEntry(untyped);
            AttributeValue<?> producerValue = immutableProducer.findEntry(attributeName);
            if (consumerValue.isPresent() && producerValue.isPresent()) {
                if (attributeMatcher.isMatching(untyped, producerValue.coerce(attribute), consumerValue.coerce(attribute))) {
                    otherValues.add("Required " + attributeName + " '" + consumerValue.get() + "' and found compatible value '" + producerValue.get() + "'.");
                } else {
                    incompatibleValues.add("Required " + attributeName + " '" + consumerValue.get() + "' and found incompatible value '" + producerValue.get() + "'.");
                }
            } else if (consumerValue.isPresent()) {
                otherValues.add("Required " + attributeName + " '" + consumerValue.get() + "' but no value provided.");
            } else {
                otherValues.add("Found " + attributeName + " '" + producerValue.get() + "' but wasn't required.");
            }
        }
        formatAttributeSection(formatter, "Incompatible attribute", incompatibleValues);
        formatAttributeSection(formatter, "Other attribute", otherValues);
        formatter.endChildren();
    }

    static void formatAttributeMatchesForAmbiguity(TreeFormatter formatter, ImmutableAttributes immutableConsumer, AttributeMatcher attributeMatcher, ImmutableAttributes immutableProducer) {
        Map<String, Attribute<?>> allAttributes = collectAttributes(immutableConsumer, immutableProducer);
        formatter.startChildren();
        List<String> compatibleValues = Lists.newArrayListWithExpectedSize(allAttributes.size());
        List<String> otherValues = Lists.newArrayListWithExpectedSize(allAttributes.size());
        for (Attribute<?> attribute : allAttributes.values()) {
            Attribute<Object> untyped = Cast.uncheckedCast(attribute);
            String attributeName = attribute.getName();
            AttributeValue<Object> consumerValue = immutableConsumer.findEntry(untyped);
            AttributeValue<?> producerValue = immutableProducer.findEntry(attributeName);
            if (consumerValue.isPresent() && producerValue.isPresent()) {
                if (attributeMatcher.isMatching(untyped, producerValue.coerce(attribute), consumerValue.coerce(attribute))) {
                    compatibleValues.add("Required " + attributeName + " '" + consumerValue.get() + "' and found compatible value '" + producerValue.get() + "'.");
                } else {
                    String result = "Required " + attributeName + " '" + consumerValue.get() + "' and found incompatible value '" + producerValue.get() + "'.";
                    assert false : "Incompatible attributes on ambiguity: " + result;
                    otherValues.add(result);
                }
            } else if (consumerValue.isPresent()) {
                otherValues.add("Required " + attributeName + " '" + consumerValue.get() + "' but no value provided.");
            } else {
                otherValues.add("Found " + attributeName + " '" + producerValue.get() + "' but wasn't required.");
            }
        }
        formatAttributeSection(formatter, "Unmatched attribute", otherValues);
        formatAttributeSection(formatter, "Compatible attribute", compatibleValues);
        formatter.endChildren();
    }

    private static Map<String, Attribute<?>> collectAttributes(ImmutableAttributes consumerAttributes, ImmutableAttributes producerAttributes) {
        Map<String, Attribute<?>> allAttributes = new TreeMap<String, Attribute<?>>();
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
