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

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ConsumerAttributeDescriber;
import org.gradle.internal.component.model.AttributeMatcher;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.logging.text.TreeFormatter;

import java.util.Map;
import java.util.TreeMap;

import static org.gradle.internal.component.AmbiguousConfigurationSelectionException.formatConfiguration;

public class NoMatchingConfigurationSelectionException extends RuntimeException {
    public NoMatchingConfigurationSelectionException(
        ConsumerAttributeDescriber describer,
        AttributeContainerInternal fromConfigurationAttributes,
        AttributeMatcher attributeMatcher,
        ComponentResolveMetadata targetComponent,
        boolean variantAware) {
        super(generateMessage(describer, fromConfigurationAttributes, attributeMatcher, targetComponent, variantAware));
    }

    private static String generateMessage(ConsumerAttributeDescriber describer, AttributeContainerInternal fromConfigurationAttributes, AttributeMatcher attributeMatcher, final ComponentResolveMetadata targetComponent, boolean variantAware) {
        Map<String, ConfigurationMetadata> configurations = new TreeMap<String, ConfigurationMetadata>();
        Optional<ImmutableList<? extends ConfigurationMetadata>> variantsForGraphTraversal = targetComponent.getVariantsForGraphTraversal();
        ImmutableList<? extends ConfigurationMetadata> variantsParticipatingInSelection = variantsForGraphTraversal.or(new LegacyConfigurationsSupplier(targetComponent));
        for (ConfigurationMetadata configurationMetadata : variantsParticipatingInSelection) {
            configurations.put(configurationMetadata.getName(), configurationMetadata);
        }
        TreeFormatter formatter = new TreeFormatter();
        if (fromConfigurationAttributes.isEmpty()) {
            formatter.node("Unable to find a matching " + (variantAware ? "variant" : "configuration") + " of " + targetComponent.getId().getDisplayName());
        } else {
            formatter.node("The consumer was configured to find " + describer.describe(fromConfigurationAttributes) + " but no matching " + (variantAware ? "variant" : "configuration") + " of " + targetComponent.getId().getDisplayName() + " was found.");
        }
        formatter.startChildren();
        if (configurations.isEmpty()) {
            formatter.node("None of the " + (variantAware ? "variants" : "consumable configurations") + " have attributes.");
        } else {
            // We're sorting the names of the configurations and later attributes
            // to make sure the output is consistently the same between invocations
            for (ConfigurationMetadata configuration : configurations.values()) {
                formatConfiguration(formatter, targetComponent, fromConfigurationAttributes, attributeMatcher, configuration, variantAware, false);
            }
        }
        formatter.endChildren();
        return formatter.toString();
    }

}
