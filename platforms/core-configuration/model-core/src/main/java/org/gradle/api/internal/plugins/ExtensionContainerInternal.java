/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.plugins;

import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.provider.Provider;
import org.gradle.api.reflect.TypeOf;

import java.util.Map;

public interface ExtensionContainerInternal extends ExtensionContainer {
    /**
     * Provides access to all known extensions.
     * @return A map of extensions, keyed by name.
     */
    Map<String, Object> getAsMap();

    /**
     * Adds a new extension to this container whose object will be calculated later, once something
     * calls a "get" method to retrieve the object from the container.
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
     * @param extensionProvider A {@link Provider} of any object implementing {@code publicType}
     * @throws IllegalArgumentException When an extension with the given name already exists.
     * @since 8.10
     */
    <T> void addLater(Class<T> publicType, String name, Provider<? extends T> extensionProvider);

    /**
     * Adds a new extension to this container whose object will be calculated later, once something
     * calls a "get" method to retrieve the object from the container.
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
     * @param extensionProvider A {@link Provider} of any object implementing {@code publicType}
     * @throws IllegalArgumentException When an extension with the given name already exists.
     * @since 8.10
     */
    <T> void addLater(TypeOf<T> publicType, String name, Provider<? extends T> extensionProvider);
}
