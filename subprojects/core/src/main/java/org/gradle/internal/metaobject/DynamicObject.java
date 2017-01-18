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

/**
 * An object that can be worked with in a dynamic fashion.
 *
 * The semantics of each method is completely up to the implementation. For example, {@link BeanDynamicObject}
 * provides a dynamic view of the functionality of an object and does not provide any decoration or extra functionality.
 * The {@link org.gradle.api.internal.ExtensibleDynamicObject} implementation on the other hand does provide extra functionality.
 */
public interface DynamicObject extends MethodAccess, PropertyAccess {
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
     * Don't use this method. Use the overload {@link PropertyAccess#getProperty(String, GetPropertyResult)} instead.
     */
    Object getProperty(String name) throws MissingPropertyException;

    /**
     * Don't use this method. Use the overload {@link PropertyAccess#setProperty(String, Object, SetPropertyResult)} instead.
     */
    void setProperty(String name, Object value) throws MissingPropertyException;

    /**
     * Don't use this method. Use the overload {@link MethodAccess#invokeMethod(String, InvokeMethodResult, Object...)} instead.
     */
    Object invokeMethod(String name, Object... arguments) throws MissingMethodException;
}
