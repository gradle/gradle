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
import org.gradle.api.internal.attributes.ImmutableAttributes;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public interface AttributeSelectionSchema {
    boolean hasAttribute(Attribute<?> attribute);

    Set<Object> disambiguate(Attribute<?> attribute, @Nullable Object requested, Set<Object> candidates);

    boolean matchValue(Attribute<?> attribute, Object requested, Object candidate);

    @Nullable
    Attribute<?> getAttribute(String name);

    /**
     * Collects attributes that were present on the candidates, but which the consumer did not ask for.
     */
    Attribute<?>[] collectExtraAttributes(ImmutableAttributes[] candidates, ImmutableAttributes requested);

    class PrecedenceResult {
        private final List<Integer> sortedIndices;
        private final Collection<Integer> unsortedIndices;

        public PrecedenceResult(List<Integer> sortedIndices, Collection<Integer> unsortedIndices) {
            this.sortedIndices = sortedIndices;
            this.unsortedIndices = unsortedIndices;
        }

        public PrecedenceResult(Collection<Integer> unsortedIndices) {
            this(Collections.emptyList(), unsortedIndices);
        }

        public List<Integer> getSortedOrder() {
            return sortedIndices;
        }

        public Collection<Integer> getUnsortedOrder() {
            return unsortedIndices;
        }
    }

    /**
     * Given a set of attributes, order those attributes based on the precedence defined by
     * this schema.
     *
     * @param requested The attributes to order. <strong>Must have a consistent iteration ordering and cannot contain duplicates</strong>.
     *
     * @return The ordered attributes.
     */
    PrecedenceResult orderByPrecedence(Collection<Attribute<?>> requested);
}
