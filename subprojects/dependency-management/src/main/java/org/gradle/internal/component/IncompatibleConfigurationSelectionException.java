/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.internal.component.model.ConfigurationGraphResolveState;
import org.gradle.internal.exceptions.StyledException;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.TreeFormatter;

import static org.gradle.internal.component.AmbiguousConfigurationSelectionException.formatConfiguration;

public class IncompatibleConfigurationSelectionException extends StyledException {
    public IncompatibleConfigurationSelectionException(
        AttributeContainerInternal fromConfigurationAttributes,
        AttributeMatcher attributeMatcher,
        ComponentGraphResolveMetadata targetComponent,
        ConfigurationGraphResolveState targetConfiguration,
        boolean variantAware,
        AttributeDescriber describer) {
        super(generateMessage(fromConfigurationAttributes, attributeMatcher, targetComponent, targetConfiguration, variantAware, describer));
    }

    private static String generateMessage(AttributeContainerInternal fromConfigurationAttributes,
                                          AttributeMatcher attributeMatcher,
                                          ComponentGraphResolveMetadata targetComponent,
                                          ConfigurationGraphResolveState targetConfiguration,
                                          boolean variantAware,
                                          AttributeDescriber describer) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node("Configuration '" + targetConfiguration.getName() + "' in " + style(StyledTextOutput.Style.Info, targetComponent.getId().getDisplayName()) + " does not match the consumer attributes");
        formatConfiguration(formatter, targetComponent, fromConfigurationAttributes, attributeMatcher, targetConfiguration.asVariant().getMetadata(), variantAware, false, describer);
        return formatter.toString();
    }

}
