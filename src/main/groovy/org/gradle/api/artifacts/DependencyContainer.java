/*
 * Copyright 2007 the original author or authors.
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

import groovy.lang.Closure;
import org.gradle.api.Project;
import org.gradle.api.specs.Spec;

import java.util.List;
import java.util.Map;

/**
 * <p>A {@code DependencyContainer} maintains a set of dependencies.</p>
 *
 * @author Hans Dockter
 */
public interface DependencyContainer {
    /**
     * The project associated with this DependencyManager
     *
     * @return an instance of a project
     */
    Project getProject();
    
    /**
     * A list of all Gradle Dependency objects.
     *
     * @see #getDependencies(org.gradle.api.specs.Spec)
     */
    List<? extends Dependency> getDependencies();

    /**
     * A list of Gradle Project Dependency based on the given {@link org.gradle.api.specs.Spec}
     *
     * @see #getDependencies()
     */
    <T extends Dependency> List<T> getDependencies(Spec<T> spec);

    /**
     * Adds dependencies. The master confs are mapped to the default configuration of
     * the dependency.
     *
     * @param confs
     * @param dependencies
     */
    void dependencies(List<String> confs, Object... dependencies);

    /**
     * Adds dependencies. The configurationMappings defines the mapping between master configurations (keys) and
     * dependency configurations (value).
     *
     * @param configurationMappings
     * @param dependencies
     */
    void dependencies(Map<Configuration, List<String>> configurationMappings, Object... dependencies);
    
    void addDependencies(Dependency... dependencies);

    /**
     * Adds a dependency. The master confs are mapped to the default configuration of
     * the dependency. The configureClosure configures this dependency.
     *
     * @param confs
     * @param userDependencyDescription
     * @param configureClosure
     * @return the added Dependency
     */
    Dependency dependency(List<String> confs, Object userDependencyDescription, Closure configureClosure);

    /**
     * Adds a dependency. The configurationMappings defines the mapping between master configurations (keys) and
     * dependency configurations (value). The configureClosure configures this dependency.
     *
     * @param configurationMappings
     * @param userDependencyDescription
     * @param configureClosure
     * @return the added Dependency
     */
    Dependency dependency(Map<Configuration, List<String>> configurationMappings, Object userDependencyDescription, Closure configureClosure);

    /**
     * Adds a client module. The given master confs are mapped to the default configuration of
     * the dependency.
     *
     * @param confs
     * @param moduleDescriptor
     * @return the added ClientModule
     */
    ClientModule clientModule(List<String> confs, String moduleDescriptor);

    /**
     * Adds a client module to the given confs. The configureClosure configures this client module.
     * See {@link org.gradle.api.internal.artifacts.dependencies.DefaultClientModule} for the API that can be used.
     *
     * @param moduleDescriptor
     * @param configureClosure
     * @return the added ClientModule
     */
    ClientModule clientModule(List<String> confs, String moduleDescriptor, Closure configureClosure);

    ClientModule clientModule(Map<Configuration, List<String>> configurationMappings, String moduleDescriptor);

    ClientModule clientModule(Map<Configuration, List<String>> configurationMappings, String moduleDescriptor, Closure configureClosure);

    ExcludeRuleContainer getExcludeRules();
}
