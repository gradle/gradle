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

package org.gradle.api.plugins;

import org.gradle.api.InvalidUserDataException;

import java.util.Map;

/**
 * Dynamic, ad-hoc, storage for Gradle domain objects.
 * <p>
 * Dynamic extensions are a lightweight mechanism for adding ad-hoc state to existing domain objects. They act like maps,
 * allowing the storage of arbitrary key/value pairs. All {@link ExtensionAware} Gradle domain objects automatically have an extension
 * named {@value #EXTENSION_NAME} of this type. All properties added to the dynamic extension become available via the owning object once set.
 * <p>
 * Dynamic extension objects support Groovy property syntax. That is, a property can be read via {@code extension.«name»} and set via {@code extension.«name» = "value"}.
 * <b>Wherever possible, the Groovy property syntax should be preferred over the {@link #get(String)} and {@link #set(String, Object)} methods.</b>
 *
 * <pre autoTested="">
 * project.ext {
 *   myprop = "a"
 * }
 * assert project.myprop == "a"
 * assert project.ext.myprop == "a"
 *
 * project.myprop = "b"
 * assert project.myprop == "b"
 * assert project.ext.myprop == "b"
 * </pre>
 *
 * You can also use the Groovy accessor syntax to get and set properties on a dynamic extension.
 *
 * <pre autoTested="">
 * project.ext["otherProp"] = "a"
 * assert project.otherProp == "a"
 * assert project.ext["otherProp"] == "a"
 * </pre>
 *
 * The exception that is thrown when an attempt is made to get the value of a property that does not exist is different depending on whether the
 * Groovy syntax is used or not. If Groovy property syntax is used, the Groovy {@link groovy.lang.MissingPropertyException} will be thrown.
 * When the {@link #get(String)} method is used, an {@link UnknownPropertyException} will be thrown.
 */
public interface DynamicPropertiesExtension {

    /**
     * The name of this extension in all {@link ExtensionContainer ExtensnionContainers}, {@value}.
     */
    public static final String EXTENSION_NAME = "ext";

    /**
     * Returns whether or not the extension has a property registered via the given name.
     *
     * @param name The name of the property to check for
     * @return {@code true} if a property has been registered with this name, otherwise {@code false}.
     */
    boolean has(String name);

    /**
     * Returns the value for the registered property with the given name.
     *
     * When using a dynamic extension from Groovy, you can also get properties via Groovy's property syntax.
     * All of the following lines of code are equivalent.
     *
     * <pre>
     * dynamicExtension.get("foo")
     * dynamicExtension.foo
     * dynamicExtension["foo"]
     * </pre>
     *
     * When using the first form, an {@link UnknownPropertyException} exception will be thrown if the
     * extension does not have a property called “{@code foo}”. When using the second forms (i.e. Groovy notation),
     * Groovy's {@link groovy.lang.MissingPropertyException} will be thrown instead.
     *
     * @param name The name of the property to get the value of
     * @return The value for the property with the given name.
     * @throws UnknownPropertyException if there is no property registered with the given name
     */
    Object get(String name) throws UnknownPropertyException;

    /**
     * Updates the value for, or creates, the registered property with the given name to the given value.
     *
     * When using a dynamic extension from Groovy, you can also set properties via Groovy's property syntax.
     * All of the following lines of code are equivalent.
     *
     * <pre>
     * dynamicExtension.set("foo", "bar")
     * dynamicExtension.foo = "bar"
     * dynamicExtension["foo"] = "bar"
     * </pre>
     *
     * @param name The name of the property to update the value of or create
     * @param value The value to set for the property
     */
    void set(String name, Object value);

    /**
     * Returns all of the registered properties and their current values as a map.
     *
     * The returned map is detached from the extension. That is, any changes made to the map do not
     * change the extension from which it originated.
     *
     * @return All of the registered properties and their current values as a map.
     */
    Map<String, Object> getProperties();

    /**
     * The exception that will be thrown when an attempt is made to read a property that is not set.
     */
    public static class UnknownPropertyException extends InvalidUserDataException {
        public UnknownPropertyException(DynamicPropertiesExtension extension, String propertyName) {
            super(String.format("cannot get property '%s' on dynamic extension '%s' as it does not exist", propertyName, extension));
        }
    }

}
