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

// TODO: We should consider renaming every concat method here to "merge" or something,
//       as these are not concatenation operations, and it is confusing to expect them
//       to behave as such.  They REPLACE duplicate keys, and sometimes result in adding
//       MULTIPLE values to the result for a single key.
@ServiceScope(Scope.BuildSession.class)
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
     * Merges the given attribute to the given container. Note: the container _should not_ contain the given attribute.
     */
    <T> ImmutableAttributes concat(ImmutableAttributes node, Attribute<T> key, T value);

    /**
     * Merges the given attribute to the given container. Note: the container _should not_ contain the given attribute.
     */
    <T> ImmutableAttributes concat(ImmutableAttributes node, Attribute<T> key, Isolatable<T> value);

    /**
     * Same as {@link #concat(ImmutableAttributes, Attribute, Isolatable)} but with special handling for legacy Usage values.
     * <p>
     * This method implements legacy behavior where we would automatically convert legacy values of
     * the {@link org.gradle.api.attributes.Usage} attribute to corresponding modern values of the Usage and
     * {@link org.gradle.api.attributes.LibraryElements} attributes.
     * <p>
     * In practice, the attribute containers should be agnostic of the actual attribute keys and values they contain.
     * Therefore, this method should be avoided and the standard transparent
     * {@link #concat(ImmutableAttributes, Attribute, Isolatable)} should be used instead.
     *
     * @deprecated In Gradle 10, we should no longer handle legacy Usage values specially,
     * and calls to this method should be replaced with the standard concat method.
     */
    @Deprecated
    <T> ImmutableAttributes concatUsageAttribute(ImmutableAttributes node, Attribute<T> key, Isolatable<T> value);

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
