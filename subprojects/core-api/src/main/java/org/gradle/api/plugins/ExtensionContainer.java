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
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.reflect.TypeOf;
import org.gradle.internal.HasInternalProtocol;

import javax.annotation.Nullable;

/**
 * Allows adding 'namespaced' DSL extensions to a target object.
 */
@HasInternalProtocol
public interface ExtensionContainer {

    /**
     * Adds a new extension to this container.
     *
     * Adding an extension of name 'foo' will:
     * <ul>
     * <li> add 'foo' dynamic property
     * <li> add 'foo' dynamic method that accepts a closure that is a configuration script block
     * </ul>
     *
     * The extension will be exposed as {@code publicType}.
     *
     * @param publicType The extension public type
     * @param name The name for the extension
     * @param extension Any object implementing {@code publicType}
     * @throws IllegalArgumentException When an extension with the given name already exists.
     * @since 3.5
     */
    <T> void add(Class<T> publicType, String name, T extension);

    /**
     * Adds a new extension to this container.
     *
     * Adding an extension of name 'foo' will:
     * <ul>
     * <li> add 'foo' dynamic property
     * <li> add 'foo' dynamic method that accepts a closure that is a configuration script block
     * </ul>
     *
     * The extension will be exposed as {@code publicType}.
     *
     * @param publicType The extension public type
     * @param name The name for the extension
     * @param extension Any object implementing {@code publicType}
     * @throws IllegalArgumentException When an extension with the given name already exists.
     * @since 3.5
     */
    <T> void add(TypeOf<T> publicType, String name, T extension);

    /**
     * Adds a new extension to this container.
     *
     * Adding an extension of name 'foo' will:
     * <ul>
     * <li> add 'foo' dynamic property
     * <li> add 'foo' dynamic method that accepts a closure that is a configuration script block
     * </ul>
     *
     * The extension will be exposed as {@code extension.getClass()} unless the extension itself declares a preferred public type via the {@link org.gradle.api.reflect.HasPublicType} protocol.
     *
     * @param name The name for the extension
     * @param extension Any object
     * @throws IllegalArgumentException When an extension with the given name already exists
     */
    void add(String name, Object extension);

    /**
     * Creates and adds a new extension to this container.
     *
     * A new instance of the given {@code instanceType} will be created using the given {@code constructionArguments}.
     * The extension will be exposed as {@code publicType}.
     * The new instance will have been dynamically made {@link ExtensionAware}, which means that you can cast it to {@link ExtensionAware}.
     *
     * @param <T> the extension public type
     * @param publicType The extension public type
     * @param name The name for the extension
     * @param instanceType The extension instance type
     * @param constructionArguments The arguments to be used to construct the extension instance
     * @return The created instance
     * @throws IllegalArgumentException When an extension with the given name already exists.
     * @see #add(Class, String, Object)
     * @since 3.5
     */
    <T> T create(Class<T> publicType, String name, Class<? extends T> instanceType, Object... constructionArguments);

    /**
     * Creates and adds a new extension to this container.
     *
     * A new instance of the given {@code instanceType} will be created using the given {@code constructionArguments}.
     * The extension will be exposed as {@code publicType}.
     * The new instance will have been dynamically made {@link ExtensionAware}, which means that you can cast it to {@link ExtensionAware}.
     *
     * @param <T> the extension public type
     * @param publicType The extension public type
     * @param name The name for the extension
     * @param instanceType The extension instance type
     * @param constructionArguments The arguments to be used to construct the extension instance
     * @return The created instance
     * @throws IllegalArgumentException When an extension with the given name already exists.
     * @see #add(Class, String, Object)
     * @since 3.5
     */
    <T> T create(TypeOf<T> publicType, String name, Class<? extends T> instanceType, Object... constructionArguments);

    /**
     * Creates and adds a new extension to this container.
     *
     * A new instance of the given {@code type} will be created using the given {@code constructionArguments}.
     * The extension will be exposed as {@code type} unless the extension itself declares a preferred public type via the {@link org.gradle.api.reflect.HasPublicType} protocol.
     * The new instance will have been dynamically made {@link ExtensionAware}, which means that you can cast it to {@link ExtensionAware}.
     *
     * @param name The name for the extension
     * @param type The type of the extension
     * @param constructionArguments The arguments to be used to construct the extension instance
     * @return The created instance
     * @throws IllegalArgumentException When an extension with the given name already exists.
     * @see #add(String, Object)
     */
    <T> T create(String name, Class<T> type, Object... constructionArguments);

    /**
     * Provides access to the schema of all known extensions.
     *
     * @since 4.5
     */
    ExtensionsSchema getExtensionsSchema();

    /**
     * Looks for the extension of a given type (useful to avoid casting). If none found it will throw an exception.
     *
     * @param type extension type
     * @return extension, never null
     * @throws UnknownDomainObjectException When the given extension is not found.
     */
    <T> T getByType(Class<T> type) throws UnknownDomainObjectException;

    /**
     * Looks for the extension of a given type (useful to avoid casting). If none found it will throw an exception.
     *
     * @param type extension type
     * @return extension, never null
     * @throws UnknownDomainObjectException When the given extension is not found.
     * @since 3.5
     */
    <T> T getByType(TypeOf<T> type) throws UnknownDomainObjectException;

    /**
     * Looks for the extension of a given type (useful to avoid casting). If none found null is returned.
     *
     * @param type extension type
     * @return extension or null
     */
    @Nullable
    <T> T findByType(Class<T> type);

    /**
     * Looks for the extension of a given type (useful to avoid casting). If none found null is returned.
     *
     * @param type extension type
     * @return extension or null
     * @since 3.5
     */
    @Nullable
    <T> T findByType(TypeOf<T> type);

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
    @Nullable
    Object findByName(String name);

    /**
     * Looks for the extension of the specified type and configures it with the supplied action.
     *
     * @param type extension type
     * @param action the configure action
     * @throws UnknownDomainObjectException if no extension is found.
     */
    <T> void configure(Class<T> type, Action<? super T> action);

    /**
     * Looks for the extension of the specified type and configures it with the supplied action.
     *
     * @param type extension type
     * @param action the configure action
     * @throws UnknownDomainObjectException if no extension is found.
     * @since 3.5
     */
    <T> void configure(TypeOf<T> type, Action<? super T> action);

    /**
     * Looks for the extension with the specified name and configures it with the supplied action.
     *
     * @param name extension name
     * @param action the configure action
     * @throws UnknownDomainObjectException if no extension is found.
     * @since 4.0
     */
    <T> void configure(String name, Action<? super T> action);

    /**
     * The extra properties extension in this extension container.
     *
     * This extension is always present in the container, with the name “ext”.
     *
     * @return The extra properties extension in this extension container.
     */
    ExtraPropertiesExtension getExtraProperties();
}
