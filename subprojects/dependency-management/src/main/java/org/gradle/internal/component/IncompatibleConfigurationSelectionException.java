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

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.text.TreeFormatter;

import java.util.Collections;
import java.util.Set;

import static org.gradle.internal.component.AmbiguousConfigurationSelectionException.ATTRIBUTE_NAME;
import static org.gradle.internal.component.AmbiguousConfigurationSelectionException.formatConfiguration;

public class IncompatibleConfigurationSelectionException extends IllegalArgumentException {
    public IncompatibleConfigurationSelectionException(
        AttributeContainer fromConfigurationAttributes,
        AttributesSchema consumerSchema,
        ComponentResolveMetadata targetComponent,
        String targetConfiguration) {
        super(generateMessage(fromConfigurationAttributes, consumerSchema, targetComponent, targetConfiguration));
    }

    private static String generateMessage(AttributeContainer fromConfigurationAttributes, AttributesSchema consumerSchema, ComponentResolveMetadata targetComponent, String targetConfiguration) {
        Set<String> requestedAttributes = Sets.newTreeSet(Iterables.transform(fromConfigurationAttributes.keySet(), ATTRIBUTE_NAME));
        TreeFormatter formatter = new TreeFormatter();
        formatter.node("Configuration '" + targetConfiguration + "' in " + targetComponent.getComponentId().getDisplayName() + " does not match the consumer attributes");
        formatConfiguration(formatter, fromConfigurationAttributes, consumerSchema, Collections.singletonList(targetComponent.getConfiguration(targetConfiguration)), requestedAttributes, targetConfiguration);
        return formatter.toString();
    }

}
