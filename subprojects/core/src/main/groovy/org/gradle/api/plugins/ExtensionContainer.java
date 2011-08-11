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

import org.gradle.api.UnknownDomainObjectException;

/**
 * Allows adding 'namespaced' DSL extensions to the target object.
 *
 * <p>Extensions can be added using a dynamic property accessor: {@code extensions.myExtension = someValue} is the same as {@code extensions.add('myExtension', someValue)}</p>
 *
 */
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
}