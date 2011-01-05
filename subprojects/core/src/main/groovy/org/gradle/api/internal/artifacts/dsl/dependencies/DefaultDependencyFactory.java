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

package org.gradle.api.internal.artifacts.dsl.dependencies;

import groovy.lang.Closure;
import org.gradle.api.GradleException;
import org.gradle.api.IllegalDependencyNotation;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.ClientModule;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ProjectDependency;

import java.util.Map;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class DefaultDependencyFactory implements DependencyFactory {
    private Set<IDependencyImplementationFactory> dependencyFactories;
    private IDependencyImplementationFactory clientModuleFactory;
    private ProjectDependencyFactory projectDependencyFactory;

    public DefaultDependencyFactory(Set<IDependencyImplementationFactory> dependencyFactories,
                                    IDependencyImplementationFactory clientModuleFactory,
                                    ProjectDependencyFactory projectDependencyFactory) {
        this.dependencyFactories = dependencyFactories;
        this.clientModuleFactory = clientModuleFactory;
        this.projectDependencyFactory = projectDependencyFactory;
    }

    public Dependency createDependency(Object dependencyNotation) {
        if (dependencyNotation instanceof Dependency) {
            return (Dependency) dependencyNotation;
        }
        
        Dependency dependency = null;
        for (IDependencyImplementationFactory factory : dependencyFactories) {
            try {
                dependency = factory.createDependency(Dependency.class, dependencyNotation);
                break;
            } catch (IllegalDependencyNotation e) {
                // ignore
            } catch (Exception e) {
                throw new GradleException(String.format("Could not create a dependency using notation: %s", dependencyNotation), e);
            }
        }

        if (dependency == null) {
            throw new InvalidUserDataException(String.format("The dependency notation: %s is invalid.",
                    dependencyNotation));
        }
        return dependency;
    }

    public ClientModule createModule(Object dependencyNotation, Closure configureClosure) {
        ClientModule clientModule = clientModuleFactory.createDependency(ClientModule.class, dependencyNotation);
        ModuleFactoryDelegate moduleFactoryDelegate = new ModuleFactoryDelegate(clientModule, this);
        moduleFactoryDelegate.prepareDelegation(configureClosure);
        if (configureClosure != null) {
            configureClosure.call();
        }
        return clientModule;
    }

    public ProjectDependency createProjectDependencyFromMap(ProjectFinder projectFinder, Map<? extends String, ? extends Object> map) {
        return projectDependencyFactory.createProjectDependencyFromMap(projectFinder, map);
    }
}
