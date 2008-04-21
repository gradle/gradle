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

import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.gradle.api.Project;

import java.util.List;

import groovy.lang.Closure;

/**
 * @author Hans Dockter
 */
public interface DependencyContainer {

    /**
     * Returns the default confs for this dependecy container.
     *
     * @return
     */
    List getDefaultConfs();

    /**
     * Sets the default confs for this dependecy container.
     * @param defaultConfs
     * @return
     */
    void setDefaultConfs(List defaultConfs);

    /**
     * The project associated with this DependencyManager
     * @return an instance of a project
     */
    Project getProject();
    
    /**
     * A list of Gradle Dependency objects.
     */
    List getDependencies();

    /**
     * A list for passing directly instances of Ivy DependencyDescriptor objects.
     */
    List getDependencyDescriptors();

    /**
     * Adds dependency descriptors to confs.
     *
     * @param confs
     * @param dependencies
     */
    void dependencies(List confs, Object[] dependencies);

    /**
     * Adds dependencies to the defaultConfs
     *
     * @param dependencies
     */
    void dependencies(Object[] dependencies);

    /**
     * Add instances of type <code>org.apache.ivy.core.module.descriptor.DependencyDescriptor</code>. Those
     * instances have an attribute to what confs they belong. There the confs don't need to be passed as an
     * argument.
     *
     * @param dependencyDescriptors
     */
    void dependencyDescriptors(DependencyDescriptor[] dependencyDescriptors);

    /**
     * Adds a module dependency to the given confs. The configureClosure configures this moduleDependency.
     * See {@link org.gradle.api.dependencies.ModuleDependency} for the API that can be used. 
     *
     * @param confs
     * @param moduleName
     * @param configureClosure
     * @return the added ModuleDependency
     */
    ModuleDependency dependency(List confs, String moduleName, Closure configureClosure);

    /**
     * Adds a module dependency to the default confs. The configureClosure configures this moduleDependency.
     * See {@link org.gradle.api.dependencies.ModuleDependency} for the API that can be used.
     *
     * @param moduleName
     * @param configureClosure
     * @return the added ModuleDependency
     */
    ModuleDependency dependency(String moduleName, Closure configureClosure);

    /**
     * Adds a client module to the given confs. The configureClosure configures this client module.
     * See {@link ClientModule} for the API that can be used.
     *
     * @param moduleName
     * @param configureClosure
     * @return the added ModuleDependency
     */
    ClientModule clientModule(List confs, String moduleName, Closure configureClosure);

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
