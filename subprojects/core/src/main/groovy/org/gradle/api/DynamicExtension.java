/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api;

/**
 * An object that allows the registration of new properties for dynamic storage.
 *
 * Dynamic extensions are intended to be used with {@link org.gradle.api.plugins.ExtensionAware} objects
 * as a lightweight mechanism for adding ad-hoc state. They act like maps, except that any attempt to read
 * or write a property that has not been “registered” with {@link #add(String, Object)} will produce an exception.
 *
 * Dynamic extension objects support Groovy property syntax. That is, once a property has been registered it
 * can be read via {@code extension.«name»} and set via {@code extension.«name» = "value"}. <b>Wherever possible,
 * the Groovy property syntax should be preferred over the {@link #get(String)} and {@link #set(String, Object)} methods.</b>
 *
 * The exception that is thrown is different depending on whether the Groovy syntax is used or not. If Groovy property
 * syntax is used, the Groovy {@link groovy.lang.MissingPropertyException} will be thrown.
 * When the {@link #get(String)} or {@link #set(String, Object)} methods are used, an {@link UnknownPropertyException} will
 * be thrown.
 */
public interface DynamicExtension {

    /**
     * Registers a new property with the given name and initial value.
     * 
     * If the property has already been registered, this is equivalent to calling {@link #set(String, Object)}.
     *
     * @param name The name of the property to add
     * @param value The initial value for the property
     */
    void add(String name, Object value);

    /**
     * Returns the value for the registered property with the given name.
     *
     * @param name The name of the property to get the value of
     * @return The value for the property with the given name.
     * @throws UnknownPropertyException if there is no property registered with the given name
     */
    Object get(String name) throws UnknownPropertyException;

    /**
     * Updates the value for the registered property with the given name to the given value.
     *
     * @param name The name of the property to update the value of
     * @param value The value to set for the property
     * @throws UnknownPropertyException if there is no property registered with the given name
     */
    void set(String name, Object value) throws UnknownPropertyException;

    /**
     * The exception that will be thrown when an attempt is made to read or write a property that is not set.
     */
    public static class UnknownPropertyException extends InvalidUserDataException {
        public UnknownPropertyException(DynamicExtension extension, String propertyName, boolean isRead) {
            super(String.format("cannot %s property '%s' on dynamic extension '%s' as it has not been added", isRead ? "get" : "set", propertyName, extension));
        }
    }

}
