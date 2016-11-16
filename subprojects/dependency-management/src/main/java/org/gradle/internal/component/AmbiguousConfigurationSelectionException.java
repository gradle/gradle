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
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Attribute;
import org.gradle.api.AttributeContainer;
import org.gradle.internal.component.model.ConfigurationMetadata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class AmbiguousConfigurationSelectionException extends IllegalArgumentException {
    private static final Function<ConfigurationMetadata, String> CONFIG_NAME = new Function<ConfigurationMetadata, String>() {
        @Override
        public String apply(ConfigurationMetadata input) {
            return input.getName();
        }
    };
    private static final Function<Attribute<?>, String> ATTRIBUTE_NAME = new Function<Attribute<?>, String>() {
        @Override
        public String apply(Attribute<?> input) {
            return input.getName();
        }
    };

    public AmbiguousConfigurationSelectionException(AttributeContainer fromConfigurationAttributes, List<ConfigurationMetadata> matches, boolean fullMatch) {
        super(generateMessage(fromConfigurationAttributes, matches, fullMatch));
    }

    private static String generateMessage(AttributeContainer fromConfigurationAttributes, List<ConfigurationMetadata> matches, boolean fullMatch) {
        Set<String> ambiguousConfigurations = Sets.newTreeSet(Lists.transform(matches, CONFIG_NAME));
        Set<String> requestedAttributes = Sets.newTreeSet(Iterables.transform(fromConfigurationAttributes.keySet(), ATTRIBUTE_NAME));
        StringBuilder sb = new StringBuilder("Cannot choose between the following configurations: ");
        sb.append(ambiguousConfigurations);
        if (fullMatch) {
            sb.append(". All of them match the consumer attributes:");
        } else {
            sb.append(". All of them partially match the consumer attributes:");
        }
        sb.append("\n");
        int maxConfLength = maxLength(ambiguousConfigurations);
        // We're sorting the names of the configurations and later attributes
        // to make sure the output is consistently the same between invocations
        for (final String ambiguousConf : ambiguousConfigurations) {
            formatAmbiguousConfiguration(sb, fromConfigurationAttributes, matches, requestedAttributes, maxConfLength, ambiguousConf);
        }
        return sb.toString();
    }

    private static void formatAmbiguousConfiguration(StringBuilder sb, AttributeContainer fromConfigurationAttributes, List<ConfigurationMetadata> matches, Set<String> requestedAttributes, int maxConfLength, final String ambiguousConf) {
        ConfigurationMetadata match = Iterables.find(matches, new Predicate<ConfigurationMetadata>() {
            @Override
            public boolean apply(ConfigurationMetadata input) {
                return ambiguousConf.equals(input.getName());
            }
        });
        AttributeContainer producerAttributes = match.getAttributes();
        Set<Attribute<?>> targetAttributes = producerAttributes.keySet();
        Set<String> targetAttributeNames = Sets.newTreeSet(Iterables.transform(targetAttributes, ATTRIBUTE_NAME));
        Set<Attribute<?>> allAttributes = Sets.union(fromConfigurationAttributes.keySet(), producerAttributes.keySet());
        Set<String> commonAttributes = Sets.intersection(requestedAttributes, targetAttributeNames);
        Set<String> consumerOnlyAttributes = Sets.difference(requestedAttributes, targetAttributeNames);
        sb.append("   ").append("- Configuration '").append(StringUtils.rightPad(ambiguousConf + "'", maxConfLength + 1)).append(" :");
        List<Attribute<?>> sortedAttributes = Ordering.usingToString().sortedCopy(allAttributes);
        List<String> values = new ArrayList<String>(sortedAttributes.size());
        formatAttributes(sb, fromConfigurationAttributes, producerAttributes, commonAttributes, consumerOnlyAttributes, sortedAttributes, values);
    }

    private static void formatAttributes(StringBuilder sb, AttributeContainer fromConfigurationAttributes, AttributeContainer producerAttributes, Set<String> commonAttributes, Set<String> consumerOnlyAttributes, List<Attribute<?>> sortedAttributes, List<String> values) {
        for (Attribute<?> attribute : sortedAttributes) {
            String attributeName = attribute.getName();
            String label;
            if (commonAttributes.contains(attributeName)) {
                label = "      - " + "Required " + attributeName + " '" + fromConfigurationAttributes.getAttribute(attribute) + "' and found compatible value '" + producerAttributes.getAttribute(attribute) + "'.";
            } else if (consumerOnlyAttributes.contains(attributeName)) {
                label = "      - " + "Required " + attributeName + " '" + fromConfigurationAttributes.getAttribute(attribute) + "' but no value provided.";
            } else {
                label = "      - " + "Found " + attributeName + " '" + producerAttributes.getAttribute(attribute) + "' but wasn't required.";
            }
            values.add(label);

        }
        sb.append("\n");
        sb.append(Joiner.on("\n").join(values));
        sb.append("\n");
    }


    private static int maxLength(Collection<String> strings) {
        return Ordering.natural().max(Iterables.transform(strings, new Function<String, Integer>() {
            @Override
            public Integer apply(String input) {
                return input.length();
            }
        }));
    }

}
