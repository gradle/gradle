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

import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.exceptions.StyledException;

import java.util.List;

@Contextual
public class VariantSelectionException extends StyledException {
    public VariantSelectionException(String message, String producerDisplayName, AttributeContainerInternal requested, List<? extends HasAttributes> candidates) {
        super(processFailureMessage(message, producerDisplayName, requested, candidates));
    }

    public VariantSelectionException(String message, Throwable cause) {
        super(message, cause);
    }

    public static VariantSelectionException selectionFailed(ResolvedVariantSet producer, Throwable failure) {
        return new VariantSelectionException(String.format("Could not select a variant of %s that matches the consumer attributes.", producer.asDescribable().getDisplayName()), failure);
    }

    private static String processFailureMessage(String message, String producerDisplayName, HasAttributes requested, List<? extends HasAttributes> candidates) {
        VariantSelectionFailureMessageProcessor processor = new VariantSelectionFailureMessageProcessor();
        return processor.process(producerDisplayName, requested, candidates).orElse(message);
    }
}
