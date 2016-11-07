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
package org.gradle.api.artifacts;

import org.gradle.api.Incubating;
import org.gradle.api.Named;
import org.gradle.internal.HasInternalProtocol;

import java.util.Set;

/**
 * A configuration may contain attributes used by the dependency resolution engine to determine what
 * to do when a configuration is consumed or resolved. Configuration attributes are represented by
 * a pair (name, type). For example, a build type can be represented by an attribute of type
 * <code>BuildType</code> named <code>buildType</code>.
 * This interface gives access to the attributes of a configuration and provides a strongly typed
 * API to read or write attributes.
 */
@Incubating
@HasInternalProtocol
public interface ConfigurationAttributes {

    /**
     * Returns the set of attribute keys of this container.
     * @return the set of attribute keys.
     */
    Set<Key<?>> keySet();

    /**
     * Sets an attribute value. It is not allowed to use <code>null</code> as
     * an attribute value.
     * @param <T> the type of the attribute
     * @param key the attribute key
     * @param value the attribute value
     * @return this container
     */
    <T> ConfigurationAttributes attribute(Key<T> key, T value);

    /**
     * Returns the value of an attribute found in this container, or <code>null</code> if
     * this container doesn't have it.
     * @param <T> the type of the attribute
     * @param key the attribute key
     * @return the attribute value, or null if not found
     */
    <T> T getAttribute(Key<T> key);

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
    boolean contains(Key<?> key);

    /**
     * Represents an attribute key, consisting of a name
     * and a type.
     * @param <T> the type of the attribute
     */
    interface Key<T> extends Named {
        /**
         * The type of the attribute.
         * @return the type of the attribute
         */
        Class<T> getType();
    }
}
