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
import org.gradle.api.internal.attributes.AttributeDescriber;
import org.gradle.internal.component.model.AttributeMatcher;
import org.gradle.internal.logging.text.TreeFormatter;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.gradle.internal.component.AmbiguousConfigurationSelectionException.formatAttributeMatchesForAmbiguity;
import static org.gradle.internal.component.AmbiguousConfigurationSelectionException.formatAttributeMatchesForIncompatibility;

public class AmbiguousVariantSelectionException extends VariantSelectionException {

    public AmbiguousVariantSelectionException(AttributeDescriber describer, String producerDisplayName, AttributeContainerInternal requested, List<? extends ResolvedVariant> matches, AttributeMatcher matcher, Set<ResolvedVariant> discarded) {
        super(format(describer, producerDisplayName, requested, matches, matcher, discarded));
    }

    private static String format(AttributeDescriber describer, String producerDisplayName, AttributeContainerInternal consumer, List<? extends ResolvedVariant> variants, AttributeMatcher matcher, Set<ResolvedVariant> discarded) {
        TreeFormatter formatter = new TreeFormatter();
        if (consumer.getAttributes().isEmpty()) {
            formatter.node("More than one variant of " + producerDisplayName + " matches the consumer attributes");
        } else {
            formatter.node("The consumer was configured to find " + describer.describeAttributeSet(consumer.asMap()) + ". However we cannot choose between the following variants of " + producerDisplayName);
        }
        formatter.startChildren();
        for (ResolvedVariant variant : variants) {
            formatter.node(variant.asDescribable().getCapitalizedDisplayName());
            formatAttributeMatchesForAmbiguity(formatter, consumer.asImmutable(), matcher, variant.getAttributes().asImmutable(), describer);
        }
        formatter.endChildren();
        if (!discarded.isEmpty()) {
            formatter.node("The following variants were also considered but didn't match the requested attributes:");
            formatter.startChildren();
            discarded.stream()
                .sorted(Comparator.comparing(v -> v.asDescribable().getCapitalizedDisplayName()))
                .forEach(discardedVariant -> {
                    formatter.node(discardedVariant.asDescribable().getCapitalizedDisplayName());
                    formatAttributeMatchesForIncompatibility(formatter, consumer.asImmutable(), matcher, discardedVariant.getAttributes().asImmutable(), describer);
                });
            formatter.endChildren();
        }
        return formatter.toString();
    }

}
