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

import org.gradle.api.attributes.AttributeContainer;
import org.gradle.internal.component.model.AttributeMatcher;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.text.TreeFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.gradle.internal.component.AmbiguousConfigurationSelectionException.formatConfiguration;

public class NoMatchingConfigurationSelectionException extends RuntimeException {
    public NoMatchingConfigurationSelectionException(
        AttributeContainer fromConfigurationAttributes,
        AttributeMatcher attributeMatcher,
        ComponentResolveMetadata targetComponent) {
        super(generateMessage(fromConfigurationAttributes, attributeMatcher, targetComponent));
    }

    private static String generateMessage(AttributeContainer fromConfigurationAttributes, AttributeMatcher attributeMatcher, ComponentResolveMetadata targetComponent) {
        TreeSet<String> configurationNames = new TreeSet<String>(targetComponent.getConfigurationNames());
        List<ConfigurationMetadata> configurations = new ArrayList<ConfigurationMetadata>(configurationNames.size());
        for (String name : configurationNames) {
            ConfigurationMetadata targetComponentConfiguration = targetComponent.getConfiguration(name);
            if (targetComponentConfiguration.isCanBeConsumed() && !targetComponentConfiguration.getAttributes().isEmpty()) {
                configurations.add(targetComponentConfiguration);
            }
        }
        TreeFormatter formatter = new TreeFormatter();
        formatter.node("Unable to find a matching configuration of " + targetComponent.getComponentId().getDisplayName());
        formatter.startChildren();
        if (configurations.isEmpty()) {
            formatter.node("None of the consumable configurations have attributes.");
        } else {
            // We're sorting the names of the configurations and later attributes
            // to make sure the output is consistently the same between invocations
            for (String config : configurationNames) {
                formatConfiguration(formatter, fromConfigurationAttributes, attributeMatcher, configurations, config);
            }
        }
        formatter.endChildren();
        return formatter.toString();
    }

}
