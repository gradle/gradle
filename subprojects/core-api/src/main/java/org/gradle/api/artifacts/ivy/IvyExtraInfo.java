/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.artifacts.ivy;

import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;

import javax.annotation.Nullable;
import javax.xml.namespace.QName;
import java.util.Map;

/**
 * Represents the set of "extra" info elements in the Ivy descriptor.  These elements
 * are children of the "ivy" element, but are not defined in the Ivy schema and come
 * from other namespaces.
 */
@Incubating
public interface IvyExtraInfo {
    /**
     * Returns the value of the element with the unique element name.  If there are multiple elements with the same element name,
     * in different namespaces, a {@link org.gradle.api.InvalidUserDataException} will be thrown.
     *
     * @param name The unique name of the element whose value should be returned
     * @return The value of the element, or null if there is no such element.
     */
    @Nullable
    String get(String name) throws InvalidUserDataException;

    /**
     * Returns the value of the element with the name and namespace provided.
     *
     * @param namespace The namespace of the element whose value should be returned
     * @param name The name of the element whose value should be returned
     * @return The value of the element, or null if there is no such element.
     */
    @Nullable
    String get(String namespace, String name);

    /**
     * Returns a map view of the 'extra' info elements such that each key is a javax.xml.namespace.QName
     * representing the namespace and name of the element and each value is the content of the element.
     *
     * @return The map view of the extra info elements. Returns an empty map if there are no elements.
     */
    Map<QName, String> asMap();
}
