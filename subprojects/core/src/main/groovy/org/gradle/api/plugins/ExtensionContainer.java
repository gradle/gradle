/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.internal.HasInternalProtocol;

/**
 * Allows adding 'namespaced' DSL extensions to a target object.
 */
@HasInternalProtocol
public interface ExtensionContainer {

    /**
     * Adding an extension of name 'foo' will:
     * <li> add 'foo' dynamic property
     * <li> add 'foo' dynamic method that accepts a closure that is a configuration script block
     *
     * @param name Will be used as a sort of namespace of properties/methods.
     * @param extension Any object whose methods and properties will extend the target object
     */
    void add(String name, Object extension);

    /**
     * Adds a new extension to this container, that itself is dynamically made {@link ExtensionAware}.
     *
     * A new instance of the given {@code type} will be created using the given {@code constructionArguments}. The new
     * instance will have been dynamically which means that you can cast the object to {@link ExtensionAware}.
     *
     * @see #add(String, Object)
     * @param name The name for the extension
     * @param type The type of the extension
     * @param constructionArguments The arguments to be used to construct the extension instance
     * @return The created instance
     */
    <T> T create(String name, Class<T> type, Object... constructionArguments);

    /**
     * Looks for the extension of a given type (useful to avoid casting). If none found it will throw an exception.
     *
     * @param type extension type
     * @return extension, never null
     * @throws UnknownDomainObjectException When the given extension is not found.
     */
    <T> T getByType(Class<T> type) throws UnknownDomainObjectException;

    /**
     * Looks for the extension of a given type (useful to avoid casting). If none found null is returned.
     *
     * @param type extension type
     * @return extension or null
     */
    <T> T findByType(Class<T> type);

    /**
     * Looks for the extension of a given name. If none found it will throw an exception.
     *
     * @param name extension name
     * @return extension, never null
     * @throws UnknownDomainObjectException When the given extension is not found.
     */
    Object getByName(String name) throws UnknownDomainObjectException;

    /**
     * Looks for the extension of a given name. If none found null is returned.
     *
     * @param name extension name
     * @return extension or null
     */
    Object findByName(String name);

    /**
     * Looks for the extension of the specified type and configures it with the supplied action.
     * @param type extension type
     * @param action the configure action
     * @throws UnknownDomainObjectException if no exception is found.
     */
    @Incubating
    <T> void configure(Class<T> type, Action<? super T> action);

    /**
     * The extra properties extension in this extension container.
     *
     * This extension is always present in the container, with the name “ext”.
     *
     * @return The extra properties extension in this extension container.
     */
    ExtraPropertiesExtension getExtraProperties();
}