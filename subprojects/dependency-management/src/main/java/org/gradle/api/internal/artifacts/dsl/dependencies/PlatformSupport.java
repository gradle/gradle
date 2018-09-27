/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.artifacts.dsl.dependencies;

import org.gradle.api.Action;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.AttributeMatchingStrategy;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.attributes.HasConfigurableAttributes;
import org.gradle.api.attributes.MultipleCandidatesDetails;
import org.gradle.api.internal.ReusableAction;

import java.util.Set;

public abstract class PlatformSupport {
    public static final Attribute<String> COMPONENT_CATEGORY = Attribute.of("org.gradle.component.category", String.class);
    public static final String LIBRARY = "library";
    public static final String REGULAR_PLATFORM = "platform";
    public static final String ENFORCED_PLATFORM = "enforced-platform";

    public static boolean isTargettingPlatform(HasConfigurableAttributes<?> target) {
        String category = target.getAttributes().getAttribute(COMPONENT_CATEGORY);
        return REGULAR_PLATFORM.equals(category) || ENFORCED_PLATFORM.equals(category);
    }

    public static void configureSchema(AttributesSchema attributesSchema) {
        AttributeMatchingStrategy<String> componentTypeMatchingStrategy = attributesSchema.attribute(PlatformSupport.COMPONENT_CATEGORY);
        componentTypeMatchingStrategy.getDisambiguationRules().add(PlatformSupport.ComponentCategoryDisambiguationRule.class);
    }

    static <T> void addPlatformAttribute(HasConfigurableAttributes<T> dependency, final String type) {
        dependency.attributes(new Action<AttributeContainer>() {
            @Override
            public void execute(AttributeContainer attributeContainer) {
                attributeContainer.attribute(COMPONENT_CATEGORY, type);
            }
        });
    }

    public static class ComponentCategoryDisambiguationRule implements AttributeDisambiguationRule<String>, ReusableAction {
        @Override
        public void execute(MultipleCandidatesDetails<String> details) {
            String consumerValue = details.getConsumerValue();
            Set<String> candidateValues = details.getCandidateValues();
            if (consumerValue == null) {
                // consumer expressed no preference, defaults to library
                if (candidateValues.contains(LIBRARY)) {
                    details.closestMatch(LIBRARY);
                    return;
                }
            }
        }

    }
}
