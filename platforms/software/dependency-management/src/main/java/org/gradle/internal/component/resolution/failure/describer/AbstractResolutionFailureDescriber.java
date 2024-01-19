/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.component.resolution.failure.describer;

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.internal.component.AbstractVariantSelectionException;
import org.gradle.internal.logging.text.TreeFormatter;

import java.util.List;

import static org.gradle.internal.component.ResolutionFailureHandler.DEFAULT_MESSAGE_PREFIX;

public abstract class AbstractResolutionFailureDescriber<T extends AbstractVariantSelectionException> implements ResolutionFailureDescriber<T> {
    protected static final String NO_MATCHING_VARIANTS_PREFIX = "No matching variant errors are explained in more detail at ";
    protected static final String NO_MATCHING_VARIANTS_SECTION = "sub:variant-no-match";

    protected final DocumentationRegistry documentationRegistry;

    protected AbstractResolutionFailureDescriber(DocumentationRegistry documentationRegistry) {
        this.documentationRegistry = documentationRegistry;
    }

    protected void suggestReviewAlgorithm(AbstractVariantSelectionException exception) {
        exception.addResolution(DEFAULT_MESSAGE_PREFIX + documentationRegistry.getDocumentationFor("variant_attributes", "sec:abm_algorithm") + ".");
    }

    protected void formatAttributeSection(TreeFormatter formatter, String section, List<String> values) {
        if (!values.isEmpty()) {
            if (values.size() > 1) {
                formatter.node(section + "s");
            } else {
                formatter.node(section);
            }
            formatter.startChildren();
            values.forEach(formatter::node);
            formatter.endChildren();
        }
    }
}
