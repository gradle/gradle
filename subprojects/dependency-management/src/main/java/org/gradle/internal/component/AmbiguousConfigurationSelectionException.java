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

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.internal.Cast;
import org.gradle.internal.component.model.AttributeMatcher;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.text.TreeFormatter;

import java.util.List;
import java.util.Set;

public class AmbiguousConfigurationSelectionException extends RuntimeException {
    private static final Function<ConfigurationMetadata, String> CONFIG_NAME = new Function<ConfigurationMetadata, String>() {
        @Override
        public String apply(ConfigurationMetadata input) {
            return input.getName();
        }
    };

    public AmbiguousConfigurationSelectionException(AttributeContainer fromConfigurationAttributes,
                                                    AttributeMatcher attributeMatcher,
                                                    List<? extends ConfigurationMetadata> matches,
                                                    ComponentResolveMetadata targetComponent) {
        super(generateMessage(fromConfigurationAttributes, attributeMatcher, matches, targetComponent));
    }

    private static String generateMessage(AttributeContainer fromConfigurationAttributes, AttributeMatcher attributeMatcher, List<? extends ConfigurationMetadata> matches, ComponentResolveMetadata targetComponent) {
        Set<String> ambiguousConfigurations = Sets.newTreeSet(Lists.transform(matches, CONFIG_NAME));
        TreeFormatter formatter = new TreeFormatter();
        formatter.node("Cannot choose between the following configurations of ");
        formatter.append(targetComponent.getComponentId().getDisplayName());
        formatter.startChildren();
        for (String configuration : ambiguousConfigurations) {
            formatter.node(configuration);
        }
        formatter.endChildren();
        formatter.node("All of them match the consumer attributes");
        // We're sorting the names of the configurations and later attributes
        // to make sure the output is consistently the same between invocations
        formatter.startChildren();
        for (String ambiguousConf : ambiguousConfigurations) {
            formatConfiguration(formatter, fromConfigurationAttributes, attributeMatcher, matches, ambiguousConf);
        }
        formatter.endChildren();
        return formatter.toString();
    }

    static void formatConfiguration(TreeFormatter formatter, AttributeContainer consumerAttributes, AttributeMatcher attributeMatcher, List<? extends ConfigurationMetadata> matches, final String conf) {
        Optional<? extends ConfigurationMetadata> match = Iterables.tryFind(matches, new Predicate<ConfigurationMetadata>() {
            @Override
            public boolean apply(ConfigurationMetadata input) {
                return conf.equals(input.getName());
            }
        });
        if (match.isPresent()) {
            AttributeContainer producerAttributes = match.get().getAttributes();
            formatter.node("Configuration '");
            formatter.append(conf);
            formatter.append("'");
            formatAttributeMatches(formatter, consumerAttributes, attributeMatcher, producerAttributes);
        }
    }

    public static void formatAttributeMatches(TreeFormatter formatter, AttributeContainer consumerAttributes, AttributeMatcher attributeMatcher, AttributeContainer producerAttributes) {
        Set<Attribute<?>> allAttributes = Sets.union(consumerAttributes.keySet(), producerAttributes.keySet());
        List<Attribute<?>> sortedAttributes = Ordering.usingToString().sortedCopy(allAttributes);
        formatter.startChildren();
        for (Attribute<?> attribute : sortedAttributes) {
            String attributeName = attribute.getName();
            if (consumerAttributes.contains(attribute) && producerAttributes.contains(attribute)) {
                Object consumerValue = consumerAttributes.getAttribute(attribute);
                Object producerValue = producerAttributes.getAttribute(attribute);
                Attribute<Object> untyped = Cast.uncheckedCast(attribute);
                if (attributeMatcher.isMatching(untyped, producerValue, consumerValue)) {
                    formatter.node("Required " + attributeName + " '" + consumerValue + "' and found compatible value '" + producerValue + "'.");
                } else {
                    formatter.node("Required " + attributeName + " '" + consumerValue + "' and found incompatible value '" + producerValue + "'.");
                }
            } else if (consumerAttributes.contains(attribute)) {
                formatter.node("Required " + attributeName + " '" + consumerAttributes.getAttribute(attribute) + "' but no value provided.");
            } else {
                formatter.node("Found " + attributeName + " '" + producerAttributes.getAttribute(attribute) + "' but wasn't required.");
            }
        }
        formatter.endChildren();
    }
}
