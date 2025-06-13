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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.attributes.Attribute;

public interface ImmutableAttributes extends AttributeContainerInternal {
    ImmutableAttributes EMPTY = new DefaultImmutableAttributesContainer();

    /**
     * Locates the entry for the given attribute. Returns a 'missing' value when not present.
     *
     * <strong>WARNING: {@link Attribute} type information is often unreliable.</strong>  Attributes created
     * from external variants that are selection candidates during resolution will <strong>NOT</strong>
     * have their type information available.
     *
     * As type is part of attribute equality, this method will in many cases <strong>NOT</strong> be useful to
     * locate these attributes within an {@link org.gradle.api.attributes.AttributeContainer} that was created
     * using the strong type information present on attribute constants such as {@link org.gradle.api.attributes.Category#CATEGORY_ATTRIBUTE}.
     *
     * You should usually prefer searching by name using {@link #findEntry(String)} to avoid these sorts of issues.
     *
     * @param key the attribute to locate in this container (name <strong>and type</strong> much match)
     * @return the value for the attribute in this container, or {@link AttributeValue#MISSING} if not present
     */
    <T> AttributeValue<T> findEntry(Attribute<T> key);

    /**
     * Locates the entry for the attribute with the given name.
     *
     * @param name the name of an attribute to locate in this container
     * @return the value for the attribute in this container, or {@link AttributeValue#MISSING} if not present
     */
    AttributeValue<?> findEntry(String name);

    @Override
    ImmutableSet<Attribute<?>> keySet();
}
