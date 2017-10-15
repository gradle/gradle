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

import org.gradle.api.attributes.Attribute;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeValue;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Cast;
import org.gradle.internal.component.model.AttributeMatcher;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.text.TreeFormatter;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class AmbiguousConfigurationSelectionException extends RuntimeException {
    public AmbiguousConfigurationSelectionException(AttributeContainerInternal fromConfigurationAttributes,
                                                    AttributeMatcher attributeMatcher,
                                                    List<? extends ConfigurationMetadata> matches,
                                                    ComponentResolveMetadata targetComponent) {
        super(generateMessage(fromConfigurationAttributes, attributeMatcher, matches, targetComponent));
    }

    private static String generateMessage(AttributeContainerInternal fromConfigurationAttributes, AttributeMatcher attributeMatcher, List<? extends ConfigurationMetadata> matches, ComponentResolveMetadata targetComponent) {
        Map<String, ConfigurationMetadata> ambiguousConfigurations = new TreeMap<String, ConfigurationMetadata>();
        for (ConfigurationMetadata match : matches) {
            ambiguousConfigurations.put(match.getName(), match);
        }
        TreeFormatter formatter = new TreeFormatter();
        formatter.node("Cannot choose between the following configurations of ");
        formatter.append(targetComponent.getComponentId().getDisplayName());
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
            formatConfiguration(formatter, fromConfigurationAttributes, attributeMatcher, ambiguousConf);
        }
        formatter.endChildren();
        return formatter.toString();
    }

    static void formatConfiguration(TreeFormatter formatter, AttributeContainerInternal consumerAttributes, AttributeMatcher attributeMatcher, ConfigurationMetadata configuration) {
        AttributeContainerInternal producerAttributes = configuration.getAttributes();
        formatter.node("Configuration '");
        formatter.append(configuration.getName());
        formatter.append("'");
        formatAttributeMatches(formatter, consumerAttributes, attributeMatcher, producerAttributes);
    }

    static void formatAttributeMatches(TreeFormatter formatter, AttributeContainerInternal consumerAttributes, AttributeMatcher attributeMatcher, AttributeContainerInternal producerAttributes) {
        Map<String, Attribute<?>> allAttributes = new TreeMap<String, Attribute<?>>();
        for (Attribute<?> attribute : producerAttributes.keySet()) {
            allAttributes.put(attribute.getName(), attribute);
        }
        for (Attribute<?> attribute : consumerAttributes.keySet()) {
            allAttributes.put(attribute.getName(), attribute);
        }
        ImmutableAttributes immmutableConsumer = consumerAttributes.asImmutable();
        ImmutableAttributes immutableProducer = producerAttributes.asImmutable();
        formatter.startChildren();
        for (Attribute<?> attribute : allAttributes.values()) {
            Attribute<Object> untyped = Cast.uncheckedCast(attribute);
            String attributeName = attribute.getName();
            AttributeValue<Object> consumerValue = immmutableConsumer.findEntry(untyped);
            AttributeValue<?> producerValue = immutableProducer.findEntry(attribute.getName());
            if (consumerValue.isPresent() && producerValue.isPresent()) {
                if (attributeMatcher.isMatching(untyped, producerValue.get(), consumerValue.get())) {
                    formatter.node("Required " + attributeName + " '" + consumerValue.get() + "' and found compatible value '" + producerValue.get() + "'.");
                } else {
                    formatter.node("Required " + attributeName + " '" + consumerValue.get() + "' and found incompatible value '" + producerValue.get() + "'.");
                }
            } else if (consumerValue.isPresent()) {
                formatter.node("Required " + attributeName + " '" + consumerValue.get() + "' but no value provided.");
            } else {
                formatter.node("Found " + attributeName + " '" + producerValue.get() + "' but wasn't required.");
            }
        }
        formatter.endChildren();
    }
}
