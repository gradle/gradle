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

import com.google.common.collect.Ordering;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.internal.component.model.AttributeMatcher;
import org.gradle.internal.text.TreeFormatter;

import java.util.List;

import static org.gradle.internal.component.AmbiguousConfigurationSelectionException.formatAttributeMatches;

public class AmbiguousVariantSelectionException extends VariantSelectionException {

    public AmbiguousVariantSelectionException(String producerDisplayName, AttributeContainerInternal requested, List<? extends ResolvedVariant> matches, AttributeMatcher matcher) {
        super(format(producerDisplayName, requested, matches, matcher));
    }

    private static String format(String producerDisplayName, AttributeContainerInternal consumer, List<? extends ResolvedVariant> variants, AttributeMatcher matcher) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node("More than one variant of " + producerDisplayName + " matches the consumer attributes");
        formatter.startChildren();
        for (ResolvedVariant variant : variants) {
            formatter.node(variant.asDescribable().getCapitalizedDisplayName());
            formatAttributeMatches(formatter, consumer, matcher, variant.getAttributes());
        }
        formatter.endChildren();
        return formatter.toString();
    }

    public static void formatAttributes(TreeFormatter formatter, AttributeContainer attributes) {
        formatter.startChildren();
        for (Attribute<?> attribute : Ordering.usingToString().sortedCopy(attributes.keySet())) {
            formatter.node(attribute.getName() + " '" + attributes.getAttribute(attribute) + "'");
        }
        formatter.endChildren();
    }

}
