/*
 * Copyright 2008 the original author or authors.
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
package org.gradle.api.internal;

import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;

import java.util.Map;

/**
 * An object that can be worked with in a dynamic fashion.
 *
 * The semantics of each method is completely up to the implementation. For example, {@link BeanDynamicObject}
 * provides a dynamic view of the functionality of an object and does not provide any decoration or extra functionality.
 * The {@link ExtensibleDynamicObject} implementation on the other hand does provide extra functionality.
 */
public interface DynamicObject {
    /**
     * Returns true when this object is known to have the given property.
     *
     * <p>Note that not every property is known. Some properties are require an attempt to get or set its value.</p>
     */
    boolean hasProperty(String name);

    /**
     * Gets the value of the given property, attaching it to the given result using {@link GetPropertyResult#result(Object)}.
     *
     * <p>Use the {@link GetPropertyResult#isFound()} method to determine whether the property has been found or not.</p>
     */
    void getProperty(String name, GetPropertyResult result);

    /**
     * Don't use this method. Use the above overload instead.
     */
    Object getProperty(String name) throws MissingPropertyException;

    /**
     * Sets the value of the given property. The implementation should call {@link SetPropertyResult#found()} when the property value has been set.
     */
    void setProperty(String name, Object value, SetPropertyResult result);

    /**
     * Don't use this method. Use the above overload instead.
     */
    void setProperty(String name, Object value) throws MissingPropertyException;

    Map<String, ?> getProperties();

    boolean hasMethod(String name, Object... arguments);

    Object invokeMethod(String name, Object... arguments) throws MissingMethodException;

    /**
     * Used to indicate that the dynamic object may still be able to invoke the method, regardless of {@link #hasMethod(String, Object...)}
     */
    boolean isMayImplementMissingMethods();
}
