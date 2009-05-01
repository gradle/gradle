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
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.IllegalDependencyNotation;
import org.gradle.api.artifacts.ClientModule;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.IDependencyImplementationFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.ModuleFactoryDelegate;
import org.gradle.util.ConfigureUtil;

import java.util.Set;

/**
 * @author Hans Dockter
 */
public class DefaultDependencyFactory implements DependencyFactory {
    private Set<IDependencyImplementationFactory> dependencyFactories;
    private ClientModuleFactory clientModuleFactory;

    public DefaultDependencyFactory(Set<IDependencyImplementationFactory> dependencyFactories, ClientModuleFactory clientModuleFactory) {
        this.dependencyFactories = dependencyFactories;
        this.clientModuleFactory = clientModuleFactory;
    }

    public Dependency createDependency(Object dependencyNotation, Closure configureClosure) {
        Dependency dependency = createDependency(dependencyNotation);
        return (Dependency) ConfigureUtil.configure(configureClosure, dependency);
    }

    public Dependency createDependency(Object dependencyNotation) {
        Dependency dependency = null;
        for (IDependencyImplementationFactory factory : dependencyFactories) {
            try {
                dependency = factory.createDependency(dependencyNotation);
                break;
            }
            catch (IllegalDependencyNotation e) {
                // ignore
            }
            catch (Exception e) {
                throw new GradleException("Dependency creation error.", e);
            }
        }

        if (dependency == null) {
            throw new InvalidUserDataException("The dependency notation: " + dependencyNotation + " is invalid!");
        }
        return dependency;
    }

    public Set<IDependencyImplementationFactory> getDependencyFactories() {
        return dependencyFactories;
    }

    public void setDependencyFactories(Set<IDependencyImplementationFactory> dependencyFactories) {
        this.dependencyFactories = dependencyFactories;
    }

    public ClientModule createModule(Object dependencyNotation, Closure configureClosure) {
        ClientModule clientModule = clientModuleFactory.createClientModule(dependencyNotation);
        ModuleFactoryDelegate moduleFactoryDelegate = new ModuleFactoryDelegate(clientModule, this);
        moduleFactoryDelegate.prepareDelegation(configureClosure);
        configureClosure.call();
        return clientModule;
    }
}
