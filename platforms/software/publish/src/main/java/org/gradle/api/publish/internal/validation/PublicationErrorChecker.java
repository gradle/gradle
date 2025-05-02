/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.publish.internal.validation;

import org.gradle.api.artifacts.PublishException;
import org.gradle.api.attributes.Category;
import org.gradle.api.component.SoftwareComponentVariant;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.component.SoftwareComponentInternal;

/**
 * Static util class containing publication checks agnostic of the publication type.
 */
public abstract class PublicationErrorChecker {
    /**
     * Checks that the given component does not have any attributes that are not allowed to be published.
     *
     * @param component the component to check
     * @param documentationRegistry for creating helpful links in error messages upon failing the check
     * @throws PublishException if the component uses attributes invalid for publication
     */
    public static void checkForUnpublishableAttributes(SoftwareComponentInternal component, DocumentationRegistry documentationRegistry) {
        for (SoftwareComponentVariant variant : component.getUsages()) {
            variant.getAttributes().keySet().stream()
                .filter(attribute -> Category.CATEGORY_ATTRIBUTE.getName().equals(attribute.getName()))
                .findFirst()
                .ifPresent(attribute -> {
                    Object value = variant.getAttributes().getAttribute(attribute);
                    if (value != null && Category.VERIFICATION.equals(value.toString())) {
                        throw new PublishException("Cannot publish module metadata for component '" + component.getName() + "' which would include a variant '" + variant.getName() + "' that contains a '" +
                            Category.CATEGORY_ATTRIBUTE.getName() + "' attribute with a value of '" + Category.VERIFICATION + "'.  This attribute is reserved for test verification output and is not publishable.  " +
                            documentationRegistry.getDocumentationRecommendationFor("on this", "variant_attributes", "sec:verification_category"));
                    }
                });
        }
    }
}
