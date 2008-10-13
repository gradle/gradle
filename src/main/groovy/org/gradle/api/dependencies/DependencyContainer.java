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

package org.gradle.api.dependencies;

import groovy.lang.Closure;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.gradle.api.Project;

import java.util.List;

/**
 * <p>A {@code DependencyContainer} maintains a set of dependencies.</p>
 *
 * @author Hans Dockter
 */
public interface DependencyContainer {
    /**
     * Returns the default configurations for this dependency container.
     *
     * @return The default configurations. Returns an empty list if there are no default configurations.
     */
    List<String> getDefaultConfs();

    /**
     * Sets the default configurations for this dependency container.
     *
     * @param defaultConfs The default configurations. May be empty, should not be null.
     */
    void setDefaultConfs(List<String> defaultConfs);

    /**
     * The project associated with this DependencyManager
     * @return an instance of a project
     */
    Project getProject();

    void setProject(Project project);
    
    /**
     * A list of all Gradle Dependency objects.
     *
     * @see #getDependencies(Filter) 
     */
    List<Dependency> getDependencies();

    /**
     * A list of Gradle Project Dependency based on the given {@link Filter}
     *
     * @see #getDependencies()
     */
    List<Dependency> getDependencies(Filter filter);

    /**
     * A list for passing directly instances of Ivy DependencyDescriptor objects.
     */
    List<DependencyDescriptor> getDependencyDescriptors();

    /**
     * Adds dependency descriptors to confs.
     *
     * @param confs
     * @param dependencies
     */
    void dependencies(List<String> confs, Object... dependencies);

    /**
     * Adds dependencies to the defaultConfs
     *
     * @param dependencies
     */
    void dependencies(Object... dependencies);

    /**
     * Add instances of type <code>org.apache.ivy.core.module.descriptor.DependencyDescriptor</code>. Those
     * instances have an attribute to what confs they belong. There the confs don't need to be passed as an
     * argument.
     *
     * @param dependencyDescriptors
     */
    void dependencyDescriptors(DependencyDescriptor... dependencyDescriptors);

    Dependency dependency(List<String> confs, Object userDependencyDescription);
    
    /**
     * Adds a dependency to the given confs. The configureClosure configures this dependency. 
     *
     * @param confs
     * @param userDependencyDescription
     * @param configureClosure
     * @return the added Dependency
     */
    Dependency dependency(List<String> confs, Object userDependencyDescription, Closure configureClosure);

    Dependency dependency(String userDependencyDescription);

    /**
     * Adds a dependency to the default confs. The configureClosure configures this dependency.
     *
     * @param userDependencyDescription
     * @param configureClosure
     * @return the added Dependency
     */
    Dependency dependency(String userDependencyDescription, Closure configureClosure);

    ClientModule clientModule(List<String> confs, String id);

    /**
     * Adds a client module to the given confs. The configureClosure configures this client module.
     * See {@link ClientModule} for the API that can be used.
     *
     * @param moduleName
     * @param configureClosure
     * @return the added ClientModule
     */
    ClientModule clientModule(List<String> confs, String moduleName, Closure configureClosure);

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
}
