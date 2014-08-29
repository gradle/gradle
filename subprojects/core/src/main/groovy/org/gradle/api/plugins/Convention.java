/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.internal.DynamicObject;

import java.util.Map;

/**
 * <p>A {@code Convention} manages a set of <i>convention objects</i>. When you add a convention object to a {@code
 * Convention}, and the properties and methods of the convention object become available as properties and methods of
 * the object which the convention is associated to. A convention object is simply a POJO or POGO. Usually, a {@code
 * Convention} is used by plugins to extend a {@link org.gradle.api.Project} or a {@link org.gradle.api.Task}.</p>
 */
public interface Convention extends ExtensionContainer {

    /**
     * Returns the plugin convention objects contained in this convention.
     *
     * @return The plugins. Returns an empty map when this convention does not contain any convention objects.
     */
    Map<String, Object> getPlugins();

    /**
     * Locates the plugin convention object with the given type.
     *
     * @param type The convention object type.
     * @return The object. Never returns null.
     * @throws IllegalStateException When there is no such object contained in this convention, or when there are
     * multiple such objects.
     */
    <T> T getPlugin(Class<T> type) throws IllegalStateException;

    /**
     * Locates the plugin convention object with the given type.
     *
     * @param type The convention object type.
     * @return The object. Returns null if there is no such object.
     * @throws IllegalStateException When there are multiple matching objects.
     */
    <T> T findPlugin(Class<T> type) throws IllegalStateException;

    /**
     * Returns a dynamic object which represents the properties and methods contributed by the extensions and convention objects contained in this
     * convention.
     *
     * @return The dynamic object
     */
    DynamicObject getExtensionsAsDynamicObject();
}
