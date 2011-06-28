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

/**
 * Allows adding 'namespaced' DSL extensions to the object.
 *
 * //TODO SF - should be called 'Extendable'?
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
    //TODO SF the method name is wrong. It should be 'put' or 'setExtension'
}