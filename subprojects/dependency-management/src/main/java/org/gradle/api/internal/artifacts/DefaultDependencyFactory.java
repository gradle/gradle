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

package org.gradle.api.internal.artifacts;

import groovy.lang.Closure;
import org.gradle.api.artifacts.ClientModule;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.ModuleFactoryDelegate;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.notations.ProjectDependencyFactory;
import org.gradle.internal.typeconversion.NotationParser;

import java.util.Map;

public class DefaultDependencyFactory implements DependencyFactory {
    private final NotationParser<Object, Dependency> dependencyNotationParser;
    private final NotationParser<Object, ClientModule> clientModuleNotationParser;
    private final ProjectDependencyFactory projectDependencyFactory;

    public DefaultDependencyFactory(NotationParser<Object, Dependency> dependencyNotationParser,
                                    NotationParser<Object, ClientModule> clientModuleNotationParser,
                                    ProjectDependencyFactory projectDependencyFactory) {
        this.dependencyNotationParser = dependencyNotationParser;
        this.clientModuleNotationParser = clientModuleNotationParser;
        this.projectDependencyFactory = projectDependencyFactory;
    }

    public Dependency createDependency(Object dependencyNotation) {
        return dependencyNotationParser.parseNotation(dependencyNotation);
    }

    public ClientModule createModule(Object dependencyNotation, Closure configureClosure) {
        ClientModule clientModule = clientModuleNotationParser.parseNotation(dependencyNotation);
        if (configureClosure != null) {
            configureModule(clientModule, configureClosure);
        }
        return clientModule;
    }

    public ProjectDependency createProjectDependencyFromMap(ProjectFinder projectFinder, Map<? extends String, ? extends Object> map) {
        return projectDependencyFactory.createFromMap(projectFinder, map);
    }

    private void configureModule(ClientModule clientModule, Closure configureClosure) {
        ModuleFactoryDelegate moduleFactoryDelegate = new ModuleFactoryDelegate(clientModule, this);
        moduleFactoryDelegate.prepareDelegation(configureClosure);
        configureClosure.call();
    }
}
