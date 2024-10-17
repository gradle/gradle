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
import org.gradle.internal.isolation.Isolatable;

import java.util.Map;

public interface AttributesFactory {
    /**
     * Returns an empty mutable attribute container.
     */
    AttributeContainerInternal mutable();

    /**
     * Returns an empty mutable attribute container with the given fallback.
     */
    AttributeContainerInternal mutable(AttributeContainerInternal fallback);

    /**
     * Returns a new attribute container which attaches a primary container and a fallback container. Changes
     * in either container are reflected in the returned container.
     * <p>
     * Despite the mutability of either {@code fallback} or {@code primary}, this method will always return
     * a non-immutable container. All mutable operations are forwarded to the primary. Therefore, if the
     * primary container is immutable, mutable operations are not supported on this container.
     *
     * @param fallback Provides base attributes.
     * @param primary Overrides any attributes in the fallback container.
     */
    AttributeContainerInternal join(AttributeContainerInternal fallback, AttributeContainerInternal primary);

    /**
     * Returns an attribute container that contains the given value.
     */
    <T> ImmutableAttributes of(Attribute<T> key, T value);

    /**
     * Returns an attribute container that contains the values in the given map
     * of attribute to attribute value.
     * <p>
     * This method is meant to be the inverse of {@link AttributeContainerInternal#asMap()}.
     *
     * @param attributes the attribute values the result should contain
     * @return immutable instance containing only the specified attributes
     */
    ImmutableAttributes fromMap(Map<Attribute<?>, ?> attributes);

    /**
     * Adds the given attribute to the given container. Note: the container _should not_ contain the given attribute.
     */
    <T> ImmutableAttributes concat(ImmutableAttributes node, Attribute<T> key, T value);

    /**
     * Adds the given attribute to the given container. Note: the container _should not_ contain the given attribute.
     */
    <T> ImmutableAttributes concat(ImmutableAttributes node, Attribute<T> key, Isolatable<T> value);

    /**
     * Merges the primary container into the fallback container and returns the result. Values in the primary container win.
     *
     * Attributes with same name but different type are considered the same attribute for the purpose of merging. As such
     * an attribute in the primary container will replace any attribute in the fallback container with the same name,
     * irrespective of the type of the attributes.
     */
    ImmutableAttributes concat(ImmutableAttributes fallback, ImmutableAttributes primary);

    /**
     * Merges the second container into the first container and returns the result. If the second container has the same
     * attribute with a different value, this method will fail instead of overriding the attribute value.
     *
     * Attributes with same name but different type are considered equal for the purpose of merging.
     */
    ImmutableAttributes safeConcat(ImmutableAttributes attributes1, ImmutableAttributes attributes2) throws AttributeMergingException;
}
