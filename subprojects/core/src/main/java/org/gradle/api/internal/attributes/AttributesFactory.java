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
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Extension to {@link BaseAttributesFactory} that can handle non-isolatable values.
 */
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
     * Adds the given attribute to the given container. Note: the container _should not_ contain the given attribute.
     */
    <T> ImmutableAttributes concat(ImmutableAttributes node, Attribute<T> key, T value);

    /**
     * @see BaseAttributesFactory#concat(ImmutableAttributes, ImmutableAttributes)
     */
    ImmutableAttributes concat(ImmutableAttributes attributes1, ImmutableAttributes attributes2);

    /**
     * @see BaseAttributesFactory#safeConcat(ImmutableAttributes, ImmutableAttributes)
     */
    ImmutableAttributes safeConcat(ImmutableAttributes attributes1, ImmutableAttributes attributes2) throws AttributeMergingException;

}
