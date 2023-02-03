/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.publish.internal.metadata;

import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Category;

import java.util.Optional;

public class EnforcedPlatformPublicationValidator implements DependencyAttributesValidator {
    private final static String SUPPRESSION = "enforced-platform";
    private final static String LONG_EXPLANATION = "In general publishing dependencies to enforced platforms is a mistake: " +
        "enforced platforms shouldn't be used for published components because they behave like forced dependencies and leak to consumers. " +
        "This can result in hard to diagnose dependency resolution errors.";

    @Override
    public String getSuppressor() {
        return SUPPRESSION;
    }

    @Override
    public String getExplanation() {
        return LONG_EXPLANATION;
    }

    @Override
    public Optional<String> validationErrorFor(String group, String name, AttributeContainer attributes) {
        Category category = attributes.getAttribute(Category.CATEGORY_ATTRIBUTE);
        if (category != null) {
            if (Category.ENFORCED_PLATFORM.equals(category.getName())) {
                return Optional.of("contains a dependency on enforced platform '" + group + ":" + name + "'");
            }
        }
        return Optional.empty();
    }
}
