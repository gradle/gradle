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
package org.gradle.api.dependencies;

import groovy.lang.Closure;

/**
 * @author Hans Dockter
 */
public interface IClientModule {
    ClientModule clientModule(String moduleName);

    /**
     * Adds a client module to the default confs. The configureClosure configures this client module.
     * See {@link ClientModule} for the API that can be used.
     *
     * @param moduleName
     * @param configureClosure
     * @return the added ModuleDependency
     */
    ClientModule clientModule(String moduleName, Closure configureClosure);

    /**
     * Adds a dependency.
     *
     * @param userDependencyDescription
     * @return The added dependency.
     * @deprecated Since Gradle 0.5, use <code>compile("org:junit:4.4")</code> instead.
     */
    Dependency dependency(String userDependencyDescription);

    /**
     * Adds a dependency. 
     * The configureClosure configures this dependency.
     *
     * @param userDependencyDescription
     * @param configureClosure
     * @return The added Dependency
     * @deprecated Since Gradle 0.5, use <code>compile("org:junit:4.4") {}</code> instead.
     */
    Dependency dependency(String userDependencyDescription, Closure configureClosure);

    /**
     * Adds dependencies to the defaultConfs
     *
     * @param dependencies
     */
    void dependencies(Object... dependencies);
    
}
