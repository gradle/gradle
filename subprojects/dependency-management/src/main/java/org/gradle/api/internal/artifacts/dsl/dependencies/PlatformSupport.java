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

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.AttributeMatchingStrategy;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.HasConfigurableAttributes;
import org.gradle.api.attributes.MultipleCandidatesDetails;
import org.gradle.api.internal.ReusableAction;
import org.gradle.api.internal.artifacts.repositories.metadata.MavenImmutableAttributesFactory;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.component.external.model.ComponentVariant;

import java.util.Set;

public abstract class PlatformSupport {

    public static final Category REGULAR_PLATFORM = NamedObjectInstantiator.INSTANCE.named(Category.class, Category.REGULAR_PLATFORM);
    public static final Category ENFORCED_PLATFORM = NamedObjectInstantiator.INSTANCE.named(Category.class, Category.ENFORCED_PLATFORM);

    public static boolean isTargettingPlatform(HasConfigurableAttributes<?> target) {
        Category category = target.getAttributes().getAttribute(Category.CATEGORY_ATTRIBUTE);
        return REGULAR_PLATFORM.equals(category) || ENFORCED_PLATFORM.equals(category);
    }

    public static void configureSchema(AttributesSchema attributesSchema) {
        AttributeMatchingStrategy<Category> componentTypeMatchingStrategy = attributesSchema.attribute(Category.CATEGORY_ATTRIBUTE);
        componentTypeMatchingStrategy.getDisambiguationRules().add(PlatformSupport.ComponentCategoryDisambiguationRule.class);
    }

    static <T> void addPlatformAttribute(HasConfigurableAttributes<T> dependency, final Category category) {
        dependency.attributes(new Action<AttributeContainer>() {
            @Override
            public void execute(AttributeContainer attributeContainer) {
                attributeContainer.attribute(Category.CATEGORY_ATTRIBUTE, category);
            }
        });
    }

    /**
     * Checks if the variant is an {@code enforced-platform} one.
     * <p>
     * This method is designed to be called on parsed metadata and thus interacts with the {@code String} version of the attribute.
     *
     * @param variant the variant to test
     * @return {@code true} if this represents an {@code enforced-platform}, {@code false} otherwise
     */
    public static boolean hasForcedDependencies(ComponentVariant variant) {
        return Objects.equal(variant.getAttributes().getAttribute(MavenImmutableAttributesFactory.CATEGORY_ATTRIBUTE), Category.ENFORCED_PLATFORM);
    }

    public static class ComponentCategoryDisambiguationRule implements AttributeDisambiguationRule<Category>, ReusableAction {
        @Override
        public void execute(MultipleCandidatesDetails<Category> details) {
            Category consumerValue = details.getConsumerValue();
            Set<Category> candidateValues = details.getCandidateValues();
            if (consumerValue == null) {
                // consumer expressed no preference, defaults to library
                candidateValues.stream()
                    .filter(it -> it.getName().equals(Category.LIBRARY))
                    .findFirst()
                    .ifPresent(it -> details.closestMatch(it));
            }
        }

    }

    public static class PreferRegularPlatform implements AttributeDisambiguationRule<Category> {
        private final static Set<Category> PLATFORM_TYPES = ImmutableSet.of(REGULAR_PLATFORM, ENFORCED_PLATFORM);

        @Override
        public void execute(MultipleCandidatesDetails<Category> details) {
            if (details.getCandidateValues().equals(PLATFORM_TYPES)) {
                details.closestMatch(REGULAR_PLATFORM);
            }
        }
    }
}
