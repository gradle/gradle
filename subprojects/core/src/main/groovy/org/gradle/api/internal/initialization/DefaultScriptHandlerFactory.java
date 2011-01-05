/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.initialization;

import org.gradle.api.Project;
import org.gradle.api.UnknownProjectException;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.Factory;
import org.gradle.api.internal.artifacts.ConfigurationContainerFactory;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyHandler;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.util.ObservableUrlClassLoader;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class DefaultScriptHandlerFactory implements ScriptHandlerFactory {
    private final Factory<? extends RepositoryHandler> repositoryHandlerFactory;
    private final ConfigurationContainerFactory configurationContainerFactory;
    private final DependencyMetaDataProvider dependencyMetaDataProvider;
    private final DependencyFactory dependencyFactory;
    private final Map<Collection<Object>, ObservableUrlClassLoader> classLoaderCache = new HashMap<Collection<Object>, ObservableUrlClassLoader>();  
    private final ProjectFinder projectFinder = new ProjectFinder() {
        public Project getProject(String path) {
            throw new UnknownProjectException("Cannot use project dependencies in a script classpath definition.");
        }
    };

    public DefaultScriptHandlerFactory(Factory<? extends RepositoryHandler> repositoryHandlerFactory,
                                       ConfigurationContainerFactory configurationContainerFactory,
                                       DependencyMetaDataProvider dependencyMetaDataProvider,
                                       DependencyFactory dependencyFactory) {
        this.repositoryHandlerFactory = repositoryHandlerFactory;
        this.configurationContainerFactory = configurationContainerFactory;
        this.dependencyMetaDataProvider = dependencyMetaDataProvider;
        this.dependencyFactory = dependencyFactory;
    }

    public ScriptHandlerInternal create(ScriptSource scriptSource, ClassLoader parentClassLoader) {
        return create(scriptSource, parentClassLoader, new BasicDomainObjectContext());
    }

    public ScriptHandlerInternal create(ScriptSource scriptSource, ClassLoader parentClassLoader,
                                        DomainObjectContext context) {
        RepositoryHandler repositoryHandler = repositoryHandlerFactory.create();
        ConfigurationContainer configurationContainer = configurationContainerFactory.createConfigurationContainer(
                repositoryHandler, dependencyMetaDataProvider, context);
        DependencyHandler dependencyHandler = new DefaultDependencyHandler(configurationContainer, dependencyFactory,
                projectFinder);
        Collection<Object> key = Arrays.asList(scriptSource.getClassName(), parentClassLoader);
        ObservableUrlClassLoader classLoader = classLoaderCache.get(key);
        if (classLoader == null) {
            classLoader = new ObservableUrlClassLoader(parentClassLoader);
            classLoaderCache.put(key, classLoader);
            return new DefaultScriptHandler(scriptSource, repositoryHandler, dependencyHandler, configurationContainer,
                    classLoader);
        }
        
        return new NoClassLoaderUpdateScriptHandler(classLoader, repositoryHandler, dependencyHandler, scriptSource, configurationContainer);
    }

    private static class BasicDomainObjectContext implements DomainObjectContext {
        public String absoluteProjectPath(String name) {
            return name;
        }
    }
}
