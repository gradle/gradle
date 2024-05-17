/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.platform.base;

import org.gradle.api.Incubating;
import org.gradle.model.internal.core.UnmanagedStruct;

import java.util.Collection;

/**
 * A container for dependency specifications.
 */
@Incubating @UnmanagedStruct
public interface DependencySpecContainer {

    /**
     * Defines a new dependency, based on a project path. The returned dependency can be mutated.
     *
     * @param path the project path
     *
     * @return a mutable dependency, added to this container
     */
    ProjectDependencySpecBuilder project(String path);

    /**
     * Defines a new dependency, based on a library name. The returned dependency can be mutated.
     *
     * @param name of the library
     *
     * @return a mutable dependency, added to this container
     */
    ProjectDependencySpecBuilder library(String name);

    /**
     * Defines a new module dependency, based on a module id or a simple name. The returned dependency can be mutated.
     *
     * @param moduleIdOrName of the module
     *
     * @return a mutable module dependency, added to this container
     */
    ModuleDependencySpecBuilder module(String moduleIdOrName);

    /**
     * Defines a new module dependency, based on a module group name. The returned dependency can be mutated.
     *
     * @param name of the module group
     *
     * @return a mutable module dependency, added to this container
     */
    ModuleDependencySpecBuilder group(String name);

    /**
     * Returns an immutable view of dependencies stored in this container.
     *
     * @return an immutable view of dependencies. Each dependency in the collection is itself immutable.
     */
    Collection<DependencySpec> getDependencies();

    /**
     * Returns true if this container doesn't hold any dependency.
     *
     * @return true if this container doesn't contain any dependency specification.
     */
    boolean isEmpty();
}
