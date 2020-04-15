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
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.TreeFormatter;

import java.util.Collection;

import static org.gradle.internal.component.AmbiguousConfigurationSelectionException.formatAttributeMatchesForIncompatibility;

public class NoMatchingVariantSelectionException extends VariantSelectionException {
    public NoMatchingVariantSelectionException(String producerDisplayName, AttributeContainerInternal consumer, Collection<? extends ResolvedVariant> candidates, AttributeMatcher matcher, AttributeDescriber describer) {
        super(format(producerDisplayName, consumer, candidates, matcher, describer));
    }

    private static String format(String producerDisplayName,
                                 AttributeContainerInternal consumer,
                                 Collection<? extends ResolvedVariant> candidates,
                                 AttributeMatcher matcher, AttributeDescriber describer) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node("No variants of " + style(StyledTextOutput.Style.Info, producerDisplayName) + " match the consumer attributes");
        formatter.startChildren();
        for (ResolvedVariant variant : candidates) {
            formatter.node(variant.asDescribable().getCapitalizedDisplayName());
            formatAttributeMatchesForIncompatibility(formatter, consumer.asImmutable(), matcher, variant.getAttributes().asImmutable(), describer);
        }
        formatter.endChildren();
        return formatter.toString();
    }
}
