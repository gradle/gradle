/*
 * Copyright 2016 the original author or authors.
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
import groovy.lang.GroovyObjectSupport;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.util.internal.ConfigureUtil;

@Deprecated
public class ModuleFactoryDelegate {
    private final org.gradle.api.artifacts.ClientModule clientModule;
    private final DependencyFactoryInternal dependencyFactory;

    public ModuleFactoryDelegate(org.gradle.api.artifacts.ClientModule clientModule, DependencyFactoryInternal dependencyFactory) {
        this.clientModule = clientModule;
        this.dependencyFactory = dependencyFactory;
    }

    @SuppressWarnings("rawtypes")
    public void prepareDelegation(Closure configureClosure) {
        ClientModuleConfigureDelegate delegate = new ClientModuleConfigureDelegate(clientModule, this);
        configureClosure.setDelegate(delegate);
        configureClosure.setResolveStrategy(Closure.DELEGATE_FIRST);
    }

    public void dependency(Object dependencyNotation) {
        dependency(dependencyNotation, null);
    }

    @SuppressWarnings("rawtypes")
    public void dependency(Object dependencyNotation, Closure configureClosure) {
        Dependency dependency = dependencyFactory.createDependency(dependencyNotation);
        clientModule.addDependency((ModuleDependency) dependency);
        ConfigureUtil.configure(configureClosure, dependency);
    }

    public void dependencies(Object[] dependencyNotations) {
        for (Object notation : dependencyNotations) {
            clientModule.addDependency((ModuleDependency) dependencyFactory.createDependency(notation));
        }
    }

    @SuppressWarnings("rawtypes")
    public void module(Object dependencyNotation, Closure configureClosure) {
        clientModule.addDependency(dependencyFactory.createModule(dependencyNotation, configureClosure));
    }

    private static class ClientModuleConfigureDelegate extends GroovyObjectSupport {
        private final ModuleFactoryDelegate moduleFactoryDelegate;
        private final org.gradle.api.artifacts.ClientModule clientModule;

        public ClientModuleConfigureDelegate(org.gradle.api.artifacts.ClientModule clientModule, ModuleFactoryDelegate moduleFactoryDelegate) {
            this.clientModule = clientModule;
            this.moduleFactoryDelegate = moduleFactoryDelegate;
        }

        @Override
        public Object invokeMethod(String name, Object args) {
            if (name.equals("dependency") || name.equals("dependencies") || name.equals("module")) {
                return InvokerHelper.invokeMethod(moduleFactoryDelegate, name, args);
            } else {
                return InvokerHelper.invokeMethod(clientModule, name, args);
            }
        }

        @Override
        public Object getProperty(String property) {
            return InvokerHelper.getProperty(clientModule, property);
        }

        @Override
        public void setProperty(String property, Object newValue) {
            InvokerHelper.setProperty(clientModule, property, newValue);
        }
    }
}
