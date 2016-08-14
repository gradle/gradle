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
 * Additional, ad-hoc, properties for Gradle domain objects.
 * <p>
 * Extra properties extensions allow new properties to be added to existing domain objects. They act like maps,
 * allowing the storage of arbitrary key/value pairs. All {@link ExtensionAware} Gradle domain objects intrinsically have an extension
 * named “{@value #EXTENSION_NAME}” of this type.
 * <p>
 * An important feature of extra properties extensions is that all of its properties are exposed for reading and writing via the {@link ExtensionAware}
 * object that owns the extension.
 *
 * <pre autoTested="">
 * project.ext.set("myProp", "myValue")
 * assert project.myProp == "myValue"
 *
 * project.myProp = "anotherValue"
 * assert project.myProp == "anotherValue"
 * assert project.ext.get("myProp") == "anotherValue"
 * </pre>
 *
 * Extra properties extension objects support Groovy property syntax. That is, a property can be read via {@code extension.«name»} and set via
 * {@code extension.«name» = "value"}. <b>Wherever possible, the Groovy property syntax should be preferred over the
 * {@link #get(String)} and {@link #set(String, Object)} methods.</b>
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
 * You can also use the Groovy accessor syntax to get and set properties on an extra properties extension.
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
 *
 */
public interface ExtraPropertiesExtension {

    /**
     * The name of this extension in all {@link ExtensionContainer ExtensionContainers}, {@value}.
     */
    String EXTENSION_NAME = "ext";

    /**
     * Returns whether or not the extension has a property registered via the given name.
     *
     * <pre autoTested="">
     * assert project.ext.has("foo") == false
     * assert project.hasProperty("foo") == false
     *
     * project.ext.foo = "bar"
     *
     * assert project.ext.has("foo")
     * assert project.hasProperty("foo")
     * </pre>
     *
     * @param name The name of the property to check for
     * @return {@code true} if a property has been registered with this name, otherwise {@code false}.
     */
    boolean has(String name);

    /**
     * Returns the value for the registered property with the given name.
     *
     * When using an extra properties extension from Groovy, you can also get properties via Groovy's property syntax.
     * All of the following lines of code are equivalent.
     *
     * <pre autoTested="">
     * project.ext { foo = "bar" }
     *
     * assert project.ext.get("foo") == "bar"
     * assert project.ext.foo == "bar"
     * assert project.ext["foo"] == "bar"
     *
     * assert project.foo == "bar"
     * assert project["foo"] == "bar"
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
     * When using an extra properties extension from Groovy, you can also set properties via Groovy's property syntax.
     * All of the following lines of code are equivalent.
     *
     * <pre autoTested="">
     * project.ext.set("foo", "bar")
     * project.ext.foo = "bar"
     * project.ext["foo"] = "bar"
     *
     * // Once the property has been created via the extension, it can be changed by the owner.
     * project.foo = "bar"
     * project["foo"] = "bar"
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
     * <pre autoTested="true">
     * project.version = "1.0"
     *
     * assert project.hasProperty("version")
     * assert project.ext.properties.containsKey("version") == false
     *
     * project.ext.foo = "bar"
     *
     * assert project.ext.properties.containsKey("foo")
     * assert project.ext.properties.foo == project.ext.foo
     *
     * assert project.ext.properties.every { key, value -> project.properties[key] == value }
     * </pre>
     *
     * @return All of the registered properties and their current values as a map.
     */
    Map<String, Object> getProperties();

    /**
     * The exception that will be thrown when an attempt is made to read a property that is not set.
     */
    class UnknownPropertyException extends InvalidUserDataException {
        public UnknownPropertyException(ExtraPropertiesExtension extension, String propertyName) {
            super(createMessage(propertyName));
        }

        public static String createMessage(String propertyName) {
            return String.format("Cannot get property '%s' on extra properties extension as it does not exist", propertyName);
        }
    }

}
