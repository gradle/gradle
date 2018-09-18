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

package org.gradle.api.attributes;

import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * An attribute container is a container of {@link Attribute attributes}, which are
 * strongly typed named entities. Such a container is responsible for storing and
 * getting attributes in a type safe way. In particular, attributes are strongly typed,
 * meaning that when we get a value from the container, the returned value type is
 * inferred from the type of the attribute. In a way, an attribute container is
 * similar to a {@link java.util.Map} where the entry is a "typed String" and the value
 * is of the string type. However the set of methods available to the container is
 * much more limited.
 *
 * It is not allowed to have two attributes with the same name but different types in
 * the container.
 *
 * @since 3.3
 */
@Incubating
@HasInternalProtocol
public interface AttributeContainer extends HasAttributes {

    /**
     * Returns the set of attribute keys of this container.
     * @return the set of attribute keys.
     */
    Set<Attribute<?>> keySet();

    /**
     * Sets an attribute value. It is not allowed to use <code>null</code> as
     * an attribute value.
     * @param <T> the type of the attribute
     * @param key the attribute key
     * @param value the attribute value
     * @return this container
     */
    <T> AttributeContainer attribute(Attribute<T> key, T value);

    /**
     * Returns the value of an attribute found in this container, or <code>null</code> if
     * this container doesn't have it.
     * @param <T> the type of the attribute
     * @param key the attribute key
     * @return the attribute value, or null if not found
     */
    @Nullable
    <T> T getAttribute(Attribute<T> key);

    /**
     * Returns true if this container is empty.
     * @return true if this container is empty.
     */
    boolean isEmpty();

    /**
     * Tells if a specific attribute is found in this container.
     * @param key the key of the attribute
     * @return true if this attribute is found in this container.
     */
    boolean contains(Attribute<?> key);

}
