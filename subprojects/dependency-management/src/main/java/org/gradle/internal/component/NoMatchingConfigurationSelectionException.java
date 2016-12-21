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

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.gradle.internal.component.AmbiguousConfigurationSelectionException.*;

public class NoMatchingConfigurationSelectionException extends IllegalArgumentException {
    public NoMatchingConfigurationSelectionException(
        AttributeContainer fromConfigurationAttributes,
        AttributesSchema consumerSchema,
        ComponentResolveMetadata targetComponent,
        List<String> candidateConfigurations) {
        super(generateMessage(fromConfigurationAttributes, consumerSchema, targetComponent, candidateConfigurations));
    }

    private static String generateMessage(AttributeContainer fromConfigurationAttributes, AttributesSchema consumerSchema, ComponentResolveMetadata targetComponent, List<String> configurationNames) {
        List<ConfigurationMetadata> configurations = new ArrayList<ConfigurationMetadata>(configurationNames.size());
        for (String name : configurationNames) {
            ConfigurationMetadata targetComponentConfiguration = targetComponent.getConfiguration(name);
            if (targetComponentConfiguration.isCanBeConsumed() && !targetComponentConfiguration.getAttributes().isEmpty()) {
                configurations.add(targetComponentConfiguration);
            }
        }
        Set<String> requestedAttributes = Sets.newTreeSet(Iterables.transform(fromConfigurationAttributes.keySet(), ATTRIBUTE_NAME));
        StringBuilder sb = new StringBuilder("Unable to find a matching configuration in '" + targetComponent +"' :");
        if (configurations.isEmpty()) {
            sb.append(" None of the consumable configurations have attributes.");
        } else {
            sb.append("\n");
            int maxConfLength = maxLength(configurationNames);
            // We're sorting the names of the configurations and later attributes
            // to make sure the output is consistently the same between invocations
            for (final String config : configurationNames) {
                formatConfiguration(sb, fromConfigurationAttributes, consumerSchema, configurations, requestedAttributes, maxConfLength, config);
            }
        }
        return sb.toString();
    }

}
