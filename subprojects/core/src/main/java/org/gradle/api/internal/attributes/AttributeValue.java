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

import org.gradle.api.attributes.Attribute;

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

        @Override
        public <S> S coerce(Attribute<S> type) {
            throw new UnsupportedOperationException("coerce() should not be called on a missing attribute value");
        }

        @Override
        public Object get() {
            throw new UnsupportedOperationException("get() should not be called on a missing attribute value");
        }
    };

    /**
     * Checks if this attribute value is present, defined as not {@code null} and not {@link AttributeValue#MISSING}
     *
     * @return {@code true} if value is present, {@code false} otherwise
     */
    boolean isPresent();

    /**
     * Returns the value of this attribute.
     * <p>
     * This should <strong>NOT</strong> be called when {@link #isPresent()} is {@code false}.
     *
     * @return the value of this attribute. Throws an error if called on a missing or unknown attribute value.
     */
    T get();

    /**
     * Coerces this value to the type of the other attribute, so it can be compared
     * to a value of that other attribute.
     * <p>
     * This should <strong>NOT</strong> be called when {@link #isPresent()} is {@code false}.
     *
     * @param otherAttribute the other attribute to attempt to coerce this attribute to
     * @throws IllegalArgumentException if this attribute is not compatible with the other one
     */
    <S> S coerce(Attribute<S> otherAttribute);
}
