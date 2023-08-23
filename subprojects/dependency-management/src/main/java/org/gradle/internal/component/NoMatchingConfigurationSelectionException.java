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

import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeDescriber;
import org.gradle.internal.component.model.AttributeMatcher;
import org.gradle.internal.component.model.ComponentGraphResolveMetadata;
import org.gradle.internal.component.model.ConfigurationGraphResolveMetadata;
import org.gradle.internal.component.model.GraphSelectionCandidates;
import org.gradle.internal.component.model.VariantGraphResolveMetadata;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.TreeFormatter;

import java.util.Map;
import java.util.TreeMap;

import static org.gradle.internal.component.AmbiguousConfigurationSelectionException.formatConfiguration;

public class NoMatchingConfigurationSelectionException extends AbstractConfigurationSelectionException {
    public NoMatchingConfigurationSelectionException(
        AttributeDescriber describer,
        AttributeContainerInternal fromConfigurationAttributes,
        AttributeMatcher attributeMatcher,
        ComponentGraphResolveMetadata targetComponent,
        GraphSelectionCandidates candidates
    ) {
        super(generateMessage(new StyledDescriber(describer), fromConfigurationAttributes, attributeMatcher, targetComponent, candidates));
    }

    private static String generateMessage(AttributeDescriber describer, AttributeContainerInternal fromConfigurationAttributes, AttributeMatcher attributeMatcher, final ComponentGraphResolveMetadata targetComponent, GraphSelectionCandidates candidates) {
        boolean variantAware = candidates.isUseVariants();
        Map<String, VariantGraphResolveMetadata> variants = new TreeMap<>();
        if (variantAware) {
            for (VariantGraphResolveState variant : candidates.getVariants()) {
                variants.put(variant.getName(), variant.getMetadata());
            }
        } else {
            for (ConfigurationGraphResolveMetadata configuration : candidates.getCandidateConfigurations()) {
                variants.put(configuration.getName(), configuration);
            }
        }
        TreeFormatter formatter = new TreeFormatter();
        String targetVariantText = style(StyledTextOutput.Style.Info, targetComponent.getId().getDisplayName());
        if (fromConfigurationAttributes.isEmpty()) {
            formatter.node("Unable to find a matching " + (variantAware ? "variant" : "configuration") + " of " + targetVariantText);
        } else {
            formatter.node("No matching " + (variantAware ? "variant" : "configuration") + " of " + targetVariantText + " was found. The consumer was configured to find " + describer.describeAttributeSet(fromConfigurationAttributes.asMap()) + " but:");
        }
        formatter.startChildren();
        if (variants.isEmpty()) {
            formatter.node("None of the " + (variantAware ? "variants" : "consumable configurations") + " have attributes.");
        } else {
            // We're sorting the names of the configurations and later attributes
            // to make sure the output is consistently the same between invocations
            for (VariantGraphResolveMetadata variant : variants.values()) {
                formatConfiguration(formatter, targetComponent, fromConfigurationAttributes, attributeMatcher, variant, variantAware, false, describer);
            }
        }
        formatter.endChildren();
        return formatter.toString();
    }

}
