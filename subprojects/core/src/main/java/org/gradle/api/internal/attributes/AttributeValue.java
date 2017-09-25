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

package org.gradle.api.internal.attributes;

import javax.annotation.Nullable;

/**
 * Represents an optional attribute value, as found in an attribute container. There are 3 possible cases:
 * <ul>
 *     <li><i>present</i> is the default, and represents an attribute with an actual value</li>
 *     <li><i>missing</i> used whenever an attribute has no value.</li>
 * </ul>
 * During attribute matching, this can be used to implement various {@link org.gradle.api.attributes.AttributeMatchingStrategy strategies}.
 * @param <T> the type of the attribute
 *
 * @since 3.3
 */
public interface AttributeValue<T> {
    AttributeValue<Object> MISSING = new AttributeValue<Object>() {
        @Override
        public boolean isPresent() {
            return false;
        }

        @Nullable
        @Override
        public <S> S coerce(Class<S> type) {
            return null;
        }

        @Override
        public Object get() {
            throw new UnsupportedOperationException("get() should not be called on a missing attribute value");
        }
    };

    /**
     * Tells if this attribute value is present.
     * @return true if this attribute value is present, implying not <code>null</code>.
     */
    boolean isPresent();

    /**
     * Returns the value of this attribute.
     * @return the value of this attribute. Throws an error if called on a missing or unknown attribute value.
     */
    T get();

    /**
     * Returns the value of this attribute as the given type, if possible.
     * @return null if cannot be converted.
     */
    @Nullable
    <S> S coerce(Class<S> type);
}
