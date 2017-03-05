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
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.AttributeMatchingStrategy;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.attributes.CompatibilityCheckDetails;
import org.gradle.api.internal.attributes.CompatibilityRuleChainInternal;
import org.gradle.internal.Cast;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.text.TreeFormatter;

import java.util.List;
import java.util.Set;

public class AmbiguousConfigurationSelectionException extends IllegalArgumentException {
    private static final Function<ConfigurationMetadata, String> CONFIG_NAME = new Function<ConfigurationMetadata, String>() {
        @Override
        public String apply(ConfigurationMetadata input) {
            return input.getName();
        }
    };
    private static final Joiner JOINER = Joiner.on(", ");
    static final Function<Attribute<?>, String> ATTRIBUTE_NAME = new Function<Attribute<?>, String>() {
        @Override
        public String apply(Attribute<?> input) {
            return input.getName();
        }
    };

    public AmbiguousConfigurationSelectionException(AttributeContainer fromConfigurationAttributes,
                                                    AttributesSchema consumerSchema,
                                                    List<? extends ConfigurationMetadata> matches,
                                                    ComponentResolveMetadata targetComponent) {
        super(generateMessage(fromConfigurationAttributes, consumerSchema, matches, targetComponent));
    }

    private static String generateMessage(AttributeContainer fromConfigurationAttributes, AttributesSchema consumerSchema, List<? extends ConfigurationMetadata> matches, ComponentResolveMetadata targetComponent) {
        Set<String> ambiguousConfigurations = Sets.newTreeSet(Lists.transform(matches, CONFIG_NAME));
        Set<String> requestedAttributes = Sets.newTreeSet(Iterables.transform(fromConfigurationAttributes.keySet(), ATTRIBUTE_NAME));
        TreeFormatter formatter = new TreeFormatter();
        formatter.node("Cannot choose between the following configurations on ");
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
        for (final String ambiguousConf : ambiguousConfigurations) {
            formatConfiguration(formatter, fromConfigurationAttributes, consumerSchema, matches, requestedAttributes, ambiguousConf);
        }
        formatter.endChildren();
        return formatter.toString();
    }

    static void formatConfiguration(TreeFormatter formatter, AttributeContainer fromConfigurationAttributes, AttributesSchema consumerSchema, List<? extends ConfigurationMetadata> matches, Set<String> requestedAttributes, final String conf) {
        Optional<? extends ConfigurationMetadata> match = Iterables.tryFind(matches, new Predicate<ConfigurationMetadata>() {
            @Override
            public boolean apply(ConfigurationMetadata input) {
                return conf.equals(input.getName());
            }
        });
        if (match.isPresent()) {
            AttributeContainer producerAttributes = match.get().getAttributes();
            Set<Attribute<?>> targetAttributes = producerAttributes.keySet();
            Set<String> targetAttributeNames = Sets.newTreeSet(Iterables.transform(targetAttributes, ATTRIBUTE_NAME));
            Set<Attribute<?>> allAttributes = Sets.union(fromConfigurationAttributes.keySet(), producerAttributes.keySet());
            Set<String> commonAttributes = Sets.intersection(requestedAttributes, targetAttributeNames);
            Set<String> consumerOnlyAttributes = Sets.difference(requestedAttributes, targetAttributeNames);
            formatter.node("Configuration '");
            formatter.append(conf);
            formatter.append("'");
            formatter.startChildren();
            List<Attribute<?>> sortedAttributes = Ordering.usingToString().sortedCopy(allAttributes);
            formatAttributes(formatter, fromConfigurationAttributes, consumerSchema, producerAttributes, commonAttributes, consumerOnlyAttributes, sortedAttributes);
            formatter.endChildren();
        }
    }

    private static void formatAttributes(final TreeFormatter formatter, AttributeContainer fromConfigurationAttributes, AttributesSchema consumerSchema, AttributeContainer producerAttributes, Set<String> commonAttributes, Set<String> consumerOnlyAttributes, List<Attribute<?>> sortedAttributes) {
        for (Attribute<?> attribute : sortedAttributes) {
            final String attributeName = attribute.getName();
            if (commonAttributes.contains(attributeName)) {
                AttributeMatchingStrategy<?> strategy = consumerSchema.getMatchingStrategy(attribute);
                CompatibilityRuleChainInternal<Object> compatibilityRules = Cast.uncheckedCast(strategy.getCompatibilityRules());
                final Object consumerValue = fromConfigurationAttributes.getAttribute(attribute);
                final Object producerValue = producerAttributes.getAttribute(attribute);
                compatibilityRules.execute(new CompatibilityCheckDetails<Object>() {
                    @Override
                    public Object getConsumerValue() {
                        return consumerValue;
                    }

                    @Override
                    public Object getProducerValue() {
                        return producerValue;
                    }

                    @Override
                    public void compatible() {
                        formatter.node("Required " + attributeName + " '" + consumerValue + "' and found compatible value '" + producerValue + "'.");
                    }

                    @Override
                    public void incompatible() {
                        formatter.node("Required " + attributeName + " '" + consumerValue + "' and found incompatible value '" + producerValue + "'.");
                    }
                });

            } else if (consumerOnlyAttributes.contains(attributeName)) {
                formatter.node("Required " + attributeName + " '" + fromConfigurationAttributes.getAttribute(attribute) + "' but no value provided.");
            } else {
                formatter.node("Found " + attributeName + " '" + producerAttributes.getAttribute(attribute) + "' but wasn't required.");
            }
        }
    }
}
