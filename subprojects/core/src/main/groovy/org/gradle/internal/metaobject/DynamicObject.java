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
package org.gradle.internal.metaobject;

import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;

import java.util.Map;

/**
 * An object that can be worked with in a dynamic fashion.
 *
 * The semantics of each method is completely up to the implementation. For example, {@link BeanDynamicObject}
 * provides a dynamic view of the functionality of an object and does not provide any decoration or extra functionality.
 * The {@link org.gradle.api.internal.ExtensibleDynamicObject} implementation on the other hand does provide extra functionality.
 */
public interface DynamicObject {
    /**
     * Creates a {@link MissingPropertyException} for getting an unknown property of this object.
     */
    MissingPropertyException getMissingProperty(String name);

    /**
     * Creates a {@link MissingPropertyException} for setting an unknown property of this object.
     */
    MissingPropertyException setMissingProperty(String name);

    /**
     * Creates a {@link MissingMethodException} for invoking an unknown method on this object.
     */
    MissingMethodException methodMissingException(String name, Object... params);

    /**
     * Returns true when this object is known to have the given property.
     *
     * <p>Note that not every property is known. Some properties require an attempt to get or set their value before they are discovered.</p>
     */
    boolean hasProperty(String name);

    /**
     * Gets the value of the given property, attaching it to the given result using {@link GetPropertyResult#result(Object)}.
     *
     * <p>Use the {@link GetPropertyResult#isFound()} method to determine whether the property has been found or not.</p>
     */
    void getProperty(String name, GetPropertyResult result);

    /**
     * Don't use this method. Use the overload above instead.
     */
    Object getProperty(String name) throws MissingPropertyException;

    /**
     * Sets the value of the given property. The implementation should call {@link SetPropertyResult#found()} when the property value has been set.
     *
     * <p>Use the {@link SetPropertyResult#isFound()} method to determine whether the property has been found or not.</p>
     */
    void setProperty(String name, Object value, SetPropertyResult result);

    /**
     * Don't use this method. Use the overload above instead.
     */
    void setProperty(String name, Object value) throws MissingPropertyException;

    Map<String, ?> getProperties();

    /**
     * Returns true when this object is known to have a method with the given name that accepts the given arguments.
     *
     * <p>Note that not every method is known. Some methods are require an attempt to get or set its value.</p>
     */
    boolean hasMethod(String name, Object... arguments);

    /**
     * Invokes the method with the given name and arguments.
     */
    void invokeMethod(String name, InvokeMethodResult result, Object... arguments);

    /**
     * Don't use this method. Use the overload above instead.
     */
    Object invokeMethod(String name, Object... arguments) throws MissingMethodException;
}
