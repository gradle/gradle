/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.artifacts;

import java.util.Set;

/**
 * To model a module in your dependency declarations. Usually you can either declare a single dependency
 * artifact or you declare a module dependency that depends on a module descriptor in a repository. With
 * a client module you can declare a module dependency without the need of a module descriptor in a
 * remote repository.
 */
public interface ClientModule extends ExternalModuleDependency {
    /**
     * Add a dependency to the client module. Such a dependency is transitive dependency for the
     * project that has a dependency on the client module.
     *  
     * @param dependency The dependency to add to the client module.
     * @see #getDependencies() 
     */
    void addDependency(ModuleDependency dependency);

    /**
     * Returns the id of the client module. This is usually only used for internal handling of the
     * client module.
     *
     * @return The id of the client module
     */
    String getId();

    /**
     * Returns all the dependencies added to the client module.
     *
     * @see #addDependency(ModuleDependency)
     */
    Set<ModuleDependency> getDependencies();

    /**
     * {@inheritDoc}
     */
    @Override
    ClientModule copy();
}
