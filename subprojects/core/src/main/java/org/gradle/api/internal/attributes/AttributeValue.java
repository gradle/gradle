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

package org.gradle.api.internal.attributes;

import org.gradle.api.InvalidUserCodeException;
import org.gradle.internal.Cast;

import java.util.NoSuchElementException;

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
public class AttributeValue<T> {
    private final static AttributeValue<Object> MISSING = new AttributeValue<Object>(null) {
        @Override
        public Object get() {
            throw new InvalidUserCodeException("get() should not be called on a missing attribute value");
        }
    };

    private final T value;

    private AttributeValue(T value) {
        this.value = value;
    }

    /**
     * Creates a valued attribute from a non-null value.
     * @param value the value of the attribute
     * @param <T> the type of the attribute
     * @return a <i>present</i> attribute value
     */
    public static <T> AttributeValue<T> of(T value) {
        return new AttributeValue<T>(value);
    }

    /**
     * Creates a missing attribute value, used to represent the fact that the attribute is known
     * but the consumer didn't want to express a value for it (it doesn't care).
     * @param <T> the type of the attribute
     * @return a <i>missing</i> attribute value
     */
    public static <T> AttributeValue<T> missing() {
        return Cast.uncheckedCast(MISSING);
    }

    /**
     * Tells if this attribute value is present.
     * @return true if this attribute value is present, implying not <code>null</code>.
     */
    public boolean isPresent() {
        return value != null;
    }

    /**
     * Returns the value of this attribute.
     * @return the value of this attribute. Throws an error if called on a missing or unknown attribute value.
     */
    public T get() {
        if (value == null) {
            throw new NoSuchElementException("No value provided");
        }
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AttributeValue<?> that = (AttributeValue<?>) o;

        return value != null ? value.equals(that.value) : that.value == null;

    }

    @Override
    public int hashCode() {
        return value != null ? value.hashCode() : 0;
    }
}
