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
package org.gradle.internal.component.model;

import org.gradle.api.attributes.Attribute;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeValue;
import org.gradle.api.internal.attributes.ImmutableAttributes;

import java.util.Collection;

public interface AttributeMatchingExplanationBuilder {
    AttributeMatchingExplanationBuilder NO_OP = new AttributeMatchingExplanationBuilder() { };

    static AttributeMatchingExplanationBuilder logging() {
        return LoggingAttributeMatchingExplanationBuilder.logging();
    }

    default void noCandidates(ImmutableAttributes requested) {

    }

    default void singleMatch(ImmutableAttributes candidate, Collection<ImmutableAttributes> candidates, AttributeContainerInternal requested) {

    }

    default void candidateDoesNotMatchAttributes(ImmutableAttributes candidate, AttributeContainerInternal requested) {

    }

    default void candidateAttributeDoesNotMatch(ImmutableAttributes candidate, Attribute<?> attribute, Object requestedValue, AttributeValue<?> candidateValue) {

    }

    default void candidateAttributeMissing(ImmutableAttributes candidate, Attribute<?> attribute, Object requestedValue) {

    }

    default void candidateIsSuperSetOfAllOthers(ImmutableAttributes candidate) {

    }
}
