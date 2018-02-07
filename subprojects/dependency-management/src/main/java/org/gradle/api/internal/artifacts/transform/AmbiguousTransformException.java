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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.internal.Pair;
import org.gradle.internal.component.VariantSelectionException;
import org.gradle.internal.text.TreeFormatter;

import java.util.List;

import static org.gradle.internal.component.AmbiguousVariantSelectionException.formatAttributes;

public class AmbiguousTransformException extends VariantSelectionException {
    public AmbiguousTransformException(String producerDisplayName, AttributeContainerInternal requested, List<Pair<ResolvedVariant, ConsumerVariantMatchResult.ConsumerVariant>> candidates) {
        super(format(producerDisplayName, requested, candidates));
    }

    private static String format(String producerDisplayName, AttributeContainerInternal requested, List<Pair<ResolvedVariant, ConsumerVariantMatchResult.ConsumerVariant>> candidates) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node("Found multiple transforms that can produce a variant of " + producerDisplayName + " for consumer attributes");
        formatAttributes(formatter, requested);
        formatter.node("Found the following transforms");
        formatter.startChildren();
        for (Pair<ResolvedVariant, ConsumerVariantMatchResult.ConsumerVariant> candidate : candidates) {
            formatter.node("Transform from " + candidate.getLeft().asDescribable().getDisplayName());
            formatAttributes(formatter, candidate.getLeft().getAttributes());
        }
        formatter.endChildren();
        return formatter.toString();
    }
}
