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
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.snapshot.impl.BooleanValueSnapshot;
import org.gradle.internal.snapshot.impl.IntegerValueSnapshot;
import org.gradle.internal.snapshot.impl.StringValueSnapshot;

import java.util.Map;

/**
 * Responsible for creating {@link AttributeContainerInternal}s and {@link Attribute}s
 * from isolated values.
 */
@ServiceScope(Scope.BuildSession.class)
public interface BaseAttributesFactory {

    /**
     * Returns an attribute container that contains the given value.
     */
    <T> ImmutableAttributes of(Attribute<T> key, Isolatable<T> value);

    /**
     * Adds the given attribute to the given container. Note: the container _should not_ contain the given attribute.
     */
    default ImmutableAttributes concat(ImmutableAttributes node, Attribute<Boolean> key, boolean value) {
        return concat(node, key, new BooleanValueSnapshot(value));
    }

    /**
     * Adds the given attribute to the given container. Note: the container _should not_ contain the given attribute.
     */
    default ImmutableAttributes concat(ImmutableAttributes node, Attribute<String> key, String value) {
        return concat(node, key, new StringValueSnapshot(value));
    }

    /**
     * Adds the given attribute to the given container. Note: the container _should not_ contain the given attribute.
     */
    default ImmutableAttributes concat(ImmutableAttributes node, Attribute<Integer> key, int value) {
        return concat(node, key, new IntegerValueSnapshot(value));
    }

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

    /**
     * Returns an attribute container that contains the values in the given map
     * of attribute to attribute value.
     * <p>
     * This method is meant to be the inverse of {@link AttributeContainerInternal#asMap()}.
     *
     * @param attributes the attribute values the result should contain
     * @return immutable instance containing only the specified attributes
     */
    ImmutableAttributes fromMap(Map<Attribute<?>, Isolatable<?>> attributes);

}
