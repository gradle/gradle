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

package org.gradle.internal.component.model;

import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeValue;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;

public interface AttributeMatcher {
    boolean isMatching(AttributeContainerInternal candidate, AttributeContainerInternal requested);

    <T> boolean isMatching(Attribute<T> attribute, T candidate, T requested);

    <T extends HasAttributes> List<T> matches(Collection<? extends T> candidates, AttributeContainerInternal requested, AttributeMatchingExplanationBuilder builder);

    <T extends HasAttributes> List<T> matches(Collection<? extends T> candidates, AttributeContainerInternal requested, @Nullable T fallback, AttributeMatchingExplanationBuilder builder);

    List<MatchingDescription<?>> describeMatching(AttributeContainerInternal candidate, AttributeContainerInternal requested);

    class MatchingDescription<T> {
        private final Attribute<T> requestedAttribute;
        private final AttributeValue<T> requestedValue;
        private final AttributeValue<T> found;
        private final boolean match;

        public MatchingDescription(Attribute<T> requestedAttribute, AttributeValue<T> requestedValue, AttributeValue<T> found, boolean match) {
            this.requestedAttribute = requestedAttribute;
            this.requestedValue = requestedValue;
            this.found = found;
            this.match = match;
        }

        public Attribute<T> getRequestedAttribute() {
            return requestedAttribute;
        }

        public AttributeValue<T> getRequestedValue() {
            return requestedValue;
        }

        public AttributeValue<T> getFound() {
            return found;
        }

        public boolean isMatch() {
            return match;
        }
    }
}
