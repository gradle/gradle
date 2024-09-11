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
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.AttributeMatchingStrategy;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.HasConfigurableAttributes;
import org.gradle.api.attributes.MultipleCandidatesDetails;
import org.gradle.api.internal.ReusableAction;
import org.gradle.api.internal.artifacts.repositories.metadata.MavenImmutableAttributesFactory;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.component.external.model.ComponentVariant;
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler;
import org.gradle.internal.component.resolution.failure.type.NoCompatibleVariantsFailure;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.inject.Inject;
import java.util.Set;

@ServiceScope(Scope.Global.class)
public class PlatformSupport {
    private final Category library;
    private final Category regularPlatform;
    private final Category enforcedPlatform;

    public PlatformSupport(NamedObjectInstantiator instantiator) {
        library = instantiator.named(Category.class, Category.LIBRARY);
        regularPlatform = instantiator.named(Category.class, Category.REGULAR_PLATFORM);
        enforcedPlatform = instantiator.named(Category.class, Category.ENFORCED_PLATFORM);
    }

    public boolean isTargetingPlatform(HasConfigurableAttributes<?> target) {
        Category category = target.getAttributes().getAttribute(Category.CATEGORY_ATTRIBUTE);
        return regularPlatform.equals(category) || enforcedPlatform.equals(category);
    }

    public Category getRegularPlatformCategory() {
        return regularPlatform;
    }

    public void configureSchema(AttributesSchemaInternal attributesSchema) {
        configureCategoryDisambiguationRule(attributesSchema);
    }
    public static void configureFailureHandler(ResolutionFailureHandler handler) {
        // TODO: This should not be here.
        // This failure handler has nothing to do with platforms.
        // This should live in JavaEcosystemSupport.
        handler.addFailureDescriber(NoCompatibleVariantsFailure.class, TargetJVMVersionOnLibraryTooNewFailureDescriber.class);
    }

    private void configureCategoryDisambiguationRule(AttributesSchema attributesSchema) {
        AttributeMatchingStrategy<Category> categorySchema = attributesSchema.attribute(Category.CATEGORY_ATTRIBUTE);
        categorySchema.getDisambiguationRules().add(ComponentCategoryDisambiguationRule.class, actionConfiguration -> {
            actionConfiguration.params(library);
            actionConfiguration.params(regularPlatform);
        });
    }

    public <T> void addPlatformAttribute(HasConfigurableAttributes<T> dependency, final Category category) {
        dependency.attributes(attributeContainer -> attributeContainer.attribute(Category.CATEGORY_ATTRIBUTE, category));
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
        final Category library;
        final Category platform;

        @Inject
        ComponentCategoryDisambiguationRule(Category library, Category regularPlatform) {
            this.library = library;
            this.platform = regularPlatform;
        }

        @Override
        public void execute(MultipleCandidatesDetails<Category> details) {
            Category consumerValue = details.getConsumerValue();
            if (consumerValue == null) {
                Set<Category> candidateValues = details.getCandidateValues();
                if (candidateValues.contains(library)) {
                    // default to library
                    details.closestMatch(library);
                } else if (candidateValues.contains(platform)) {
                    // default to normal platform when only platforms are available and nothing has been requested
                    details.closestMatch(platform);
                }
            }
        }
    }
}
