/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal;

import org.gradle.api.Attribute;
import org.gradle.api.AttributeContainer;

import java.util.Collections;
import java.util.Set;

public interface AttributeContainerInternal extends AttributeContainer {
    /**
     * An immutable empty configuration attributes map.
     */
    AttributeContainerInternal EMPTY = new AttributeContainerInternal() {
        @Override
        public Set<Attribute<?>> keySet() {
            return Collections.emptySet();
        }

        @Override
        public <T> AttributeContainer attribute(Attribute<T> key, T value) {
            throw new UnsupportedOperationException("Mutation of attributes is not allowed");
        }

        @Override
        public <T> T getAttribute(Attribute<T> key) {
            return null;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public boolean contains(Attribute<?> key) {
            return false;
        }

        @Override
        public AttributeContainer asImmutable() {
            return this;
        }

        @Override
        public AttributeContainer copy() {
            return new DefaultAttributeContainer();
        }

        @Override
        public AttributeContainer getAttributes() {
            return null;
        }
    };

    /**
     * Returns an immutable view of this attribute set. Implementations are not required to return a distinct
     * instance for each call.
     * @return an immutable view of this container.
     */
    AttributeContainer asImmutable();

    AttributeContainer copy();
}
