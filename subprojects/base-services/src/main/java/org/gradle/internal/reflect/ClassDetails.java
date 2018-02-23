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

package org.gradle.internal.reflect;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface ClassDetails {
    /**
     * Returns the non-private properties of this class. Includes inherited properties
     */
    Set<String> getPropertyNames();

    /**
     * Returns the non-private properties of this class. Includes inherited properties
     */
    Collection<? extends PropertyDetails> getProperties();

    /**
     * Returns the details of a non-private property of this class.
     */
    PropertyDetails getProperty(String name) throws NoSuchPropertyException;

    /**
     * Returns all methods of this class, including all inherited, private and static methods.
     */
    List<Method> getAllMethods();

    /**
     * Returns the non-private instance methods of this class that are not property getter or setter methods.
     * Includes inherited methods.
     */
    List<Method> getInstanceMethods();

    /**
     * The ordered super types of this type.
     *
     * Entries are ordered by their “distance” from the target type, nearest to furthest.
     * Superclasses are considered nearer than implemented interfaces.
     * Interfaces are ordered by declaration order.
     */
    Set<Class<?>> getSuperTypes();
}
