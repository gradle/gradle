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

package org.gradle.internal.component.model;

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.AmbiguousConfigurationSelectionException;
import org.gradle.internal.component.NoMatchingConfigurationSelectionException;

import java.util.List;

public abstract class AbstractDependencyMetadata implements DependencyMetadata {

    /**
     * Should be extracted out as a service.
     */
    protected ConfigurationMetadata selectConfigurationUsingAttributeMatching(ImmutableAttributes consumerAttributes, ComponentResolveMetadata targetComponent, AttributesSchemaInternal consumerSchema) {
        List<? extends ConfigurationMetadata> consumableConfigurations = targetComponent.getVariantsForGraphTraversal();
        AttributesSchemaInternal producerAttributeSchema = targetComponent.getAttributesSchema();
        AttributeMatcher attributeMatcher = consumerSchema.withProducer(producerAttributeSchema);
        ConfigurationMetadata fallbackConfiguration = targetComponent.getConfiguration(Dependency.DEFAULT_CONFIGURATION);
        if (fallbackConfiguration != null && !fallbackConfiguration.isCanBeConsumed()) {
            fallbackConfiguration = null;
        }
        List<ConfigurationMetadata> matches = attributeMatcher.matches(consumableConfigurations, consumerAttributes, fallbackConfiguration);
        if (matches.size() == 1) {
            return matches.get(0);
        } else if (!matches.isEmpty()) {
            throw new AmbiguousConfigurationSelectionException(consumerAttributes, attributeMatcher, matches, targetComponent);
        } else {
            throw new NoMatchingConfigurationSelectionException(consumerAttributes, attributeMatcher, targetComponent);
        }
    }
}
