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

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.internal.component.model.AttributeMatcher;
import org.gradle.internal.text.TreeFormatter;

import java.util.Collection;

import static org.gradle.internal.component.AmbiguousConfigurationSelectionException.formatAttributeMatches;

public class NoMatchingVariantSelectionException extends RuntimeException {
    public NoMatchingVariantSelectionException(AttributeContainerInternal consumer, Collection<? extends ResolvedVariant> candidates, AttributeMatcher matcher) {
        super(format(consumer, candidates, matcher));
    }

    private static String format(AttributeContainerInternal consumer, Collection<? extends ResolvedVariant> candidates, AttributeMatcher matcher) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node("No variants match the consumer attributes");
        formatter.startChildren();
        for (ResolvedVariant variant : candidates) {
            formatter.node("Variant");
            formatAttributeMatches(formatter, consumer, matcher, variant.getAttributes());
        }
        formatter.endChildren();
        return formatter.toString();
    }
}
